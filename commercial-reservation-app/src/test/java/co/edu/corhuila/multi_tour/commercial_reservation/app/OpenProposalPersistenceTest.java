package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcOpenProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalAnswer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-014 plan D-05 / AC-14.8 (declared debt DD-3): "is a rescheduling proposal open on the reservation" is read from the proposals of HU-COM-018:
// a PENDING proposal inside an OPEN episode. An open episode with no proposal yet, an answered, voided or expired proposal, another tenant and
// another reservation all answer false. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class OpenProposalPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private static final Instant CANCELLED_AT = NOW.minus(Duration.ofHours(30));
    private static final Instant START = NOW.plus(Duration.ofDays(40));

    private ReservationPersistenceFixture fixture;
    private JdbcReschedulingRepository episodes;
    private JdbcOpenProposal proposals;
    private UUID reservationId;
    private UUID originalDeparture;
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        episodes = new JdbcReschedulingRepository(fixture.jdbc);
        proposals = new JdbcOpenProposal(fixture.jdbc);
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
    void anOpenEpisodeWithoutAProposalHasNoOpenProposal() {
        assertThat(proposals.isOpen(fixture.tenant, reservationId)).isFalse();
        episodes.save(open());
        assertThat(proposals.isOpen(fixture.tenant, reservationId)).isFalse();
    }

    @Test
    void aPendingProposalOfAnOpenEpisodeIsOpen() {
        episodes.save(proposed(open()));
        assertThat(proposals.isOpen(fixture.tenant, reservationId)).isTrue();
    }

    @Test
    void aVoidedProposalIsNotOpen() {
        ReschedulingEpisode first = proposed(open());
        episodes.save(first);
        episodes.save(first.voidPending(CANCELLED_AT.plus(Duration.ofHours(5))));

        assertThat(proposals.isOpen(fixture.tenant, reservationId)).isFalse();
    }

    @Test
    void anAnsweredProposalIsNotOpen() {
        ReschedulingEpisode proposed = proposed(open());
        episodes.save(proposed);
        Instant answeredAt = NOW.minus(Duration.ofHours(1));

        episodes.save(proposed.answer(ProposalAnswer.outside(true, admin, "PHONE", "Agreed by phone", answeredAt.minusSeconds(60)), answeredAt));

        assertThat(proposals.isOpen(fixture.tenant, reservationId)).isFalse();
    }

    @Test
    void anExpiredProposalIsNotOpen() {
        ReschedulingEpisode proposed = proposed(open());
        episodes.save(proposed);

        episodes.save(proposed.expire(proposed.episodeLimit()));

        assertThat(proposals.isOpen(fixture.tenant, reservationId)).isFalse();
    }

    @Test
    void anotherTenantAndAnotherReservationHaveNoOpenProposal() {
        episodes.save(proposed(open()));
        assertThat(proposals.isOpen(UUID.randomUUID(), reservationId)).isFalse();
        assertThat(proposals.isOpen(fixture.tenant, UUID.randomUUID())).isFalse();
    }
}
