package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ClosingReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalAnswer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-018 over the real schema (api tasks T-01, T-02, T-04; AC-18.5, AC-18.8, AC-18.14, AC-18.18; db slice 1, tables rescheduling_episode and rescheduling_proposal and their guards): the episode
// and its proposals are stored and read back whole, one open episode per reservation, a closed row is never written again, a proposal is answered once, and another tenant sees nothing.
// Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReschedulingPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private static final Instant CANCELLED_AT = NOW.minus(Duration.ofHours(30));
    private static final Instant START = NOW.plus(Duration.ofDays(40));

    private ReservationPersistenceFixture fixture;
    private JdbcReschedulingRepository episodes;
    private UUID reservationId;
    private UUID originalDeparture;
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        episodes = new JdbcReschedulingRepository(fixture.jdbc);
        var reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        reservationId = UUID.randomUUID();
        var reservation = fixture.reservation(reservationId, true);
        reservations.save(reservation);
        originalDeparture = reservation.departureId();
    }

    private ReschedulingEpisode open() {
        return ReschedulingEpisode.open(UUID.randomUUID(), fixture.tenant, reservationId, originalDeparture, CANCELLED_AT, NOW);
    }

    private ReschedulingEpisode proposed(ReschedulingEpisode episode) {
        return episode.propose(UUID.randomUUID(), UUID.randomUUID(), 2, admin, CANCELLED_AT.plus(Duration.ofHours(2)), START);
    }

    @Test
    void anOpenEpisodeIsStoredWithItsClocksAndFoundByIdAndByIncident() {
        ReschedulingEpisode episode = open();

        episodes.save(episode);

        ReschedulingEpisode stored = episodes.find(fixture.tenant, episode.id()).orElseThrow();
        assertThat(stored).isEqualTo(episode);
        assertThat(stored.proposalPeriodEnd()).isEqualTo(CANCELLED_AT.plus(Duration.ofHours(24)));
        assertThat(stored.episodeLimit()).isEqualTo(CANCELLED_AT.plus(Duration.ofHours(96)));
        assertThat(episodes.findByIncident(fixture.tenant, reservationId, originalDeparture)).contains(stored);
        episodes.save(episode);
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.rescheduling_episode WHERE tenant_id = ?", Integer.class, fixture.tenant)).isEqualTo(1);
    }

    @Test
    void aProposalIsStoredWithItsDeadlineAndItsAnswerOnlyOnce() {
        ReschedulingEpisode episode = proposed(open());
        episodes.save(episode);
        Instant answeredAt = NOW.minus(Duration.ofHours(1));

        ReschedulingEpisode accepted = episode.answer(ProposalAnswer.outside(true, admin, "PHONE", "Called the agency and agreed", answeredAt.minusSeconds(60)), answeredAt);
        episodes.save(accepted);

        ReschedulingEpisode stored = episodes.find(fixture.tenant, episode.id()).orElseThrow();
        assertThat(stored.state()).isEqualTo(EpisodeState.RESCHEDULED);
        assertThat(stored.reason()).isEqualTo(ClosingReason.ACCEPTED);
        assertThat(stored.proposals()).singleElement().satisfies(p -> {
            assertThat(p.state()).isEqualTo(ProposalState.ACCEPTED);
            assertThat(p.answer().channel()).isEqualTo("PHONE");
            assertThat(p.answer().evidence()).isEqualTo("Called the agency and agreed");
            assertThat(p.answer().recordedBy()).isEqualTo(admin);
            assertThat(p.responseDeadlineAt()).isEqualTo(accepted.proposals().get(0).responseDeadlineAt());
        });
        episodes.save(accepted);
        assertThat(episodes.find(fixture.tenant, episode.id()).orElseThrow()).isEqualTo(stored);
    }

    @Test
    void aVoidedProposalAndItsSubstituteAreBothKept() {
        ReschedulingEpisode first = proposed(open());
        episodes.save(first);
        ReschedulingEpisode voided = first.voidPending(CANCELLED_AT.plus(Duration.ofHours(5)));
        episodes.save(voided);
        ReschedulingEpisode second = voided.propose(UUID.randomUUID(), UUID.randomUUID(), 2, admin, CANCELLED_AT.plus(Duration.ofHours(6)), START);
        episodes.save(second);

        ReschedulingEpisode stored = episodes.find(fixture.tenant, first.id()).orElseThrow();
        assertThat(stored.proposals()).extracting(p -> p.state()).containsExactly(ProposalState.VOID, ProposalState.PENDING);
        assertThat(stored.proposalPeriodEnd()).isEqualTo(second.proposalPeriodEnd());
    }

    @Test
    void anExpiryClosesTheEpisodeAndAClosedEpisodeIsNeverWrittenAgain() {
        ReschedulingEpisode episode = proposed(open());
        episodes.save(episode);
        Instant limit = episode.episodeLimit();

        ReschedulingEpisode expired = episode.expire(limit);
        episodes.save(expired);

        ReschedulingEpisode stored = episodes.find(fixture.tenant, episode.id()).orElseThrow();
        assertThat(stored.state()).isEqualTo(EpisodeState.CANCELLED);
        assertThat(stored.reason()).isEqualTo(ClosingReason.EPISODE_LIMIT_REACHED);
        assertThat(stored.proposals().get(0).state()).isEqualTo(ProposalState.EXPIRED);
        episodes.save(expired);
        assertThat(episodes.find(fixture.tenant, episode.id()).orElseThrow()).isEqualTo(stored);
    }

    @Test
    void thereIsOneOpenEpisodePerReservationAndAnotherTenantSeesNothing() {
        ReschedulingEpisode episode = open();
        episodes.save(episode);

        assertThatThrownBy(() -> episodes.save(ReschedulingEpisode.open(UUID.randomUUID(), fixture.tenant, reservationId, UUID.randomUUID(), CANCELLED_AT, NOW)))
                .hasMessageContaining("uq_rescheduling_episode_open_reservation");
        assertThat(episodes.find(UUID.randomUUID(), episode.id())).isEmpty();
        assertThat(episodes.findByIncident(UUID.randomUUID(), reservationId, originalDeparture)).isEmpty();
    }
}
