package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReprogrammingMark;
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

// HU-COM-013 AC-13.6 / RN-07, declared debt DD-5: the mark "Pendiente de respuesta de reprogramacion" is read from the episode of HU-COM-018
// (db tables rescheduling_episode). A reservation is marked while its episode is open and not before or after; another tenant never sees it.
// Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReprogrammingMarkPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private static final Instant CANCELLED_AT = NOW.minus(Duration.ofHours(30));
    private static final Instant START = NOW.plus(Duration.ofDays(40));

    private ReservationPersistenceFixture fixture;
    private JdbcReschedulingRepository episodes;
    private JdbcReprogrammingMark marks;
    private UUID reservationId;
    private UUID originalDeparture;
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        episodes = new JdbcReschedulingRepository(fixture.jdbc);
        marks = new JdbcReprogrammingMark(fixture.jdbc);
        var reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        reservationId = UUID.randomUUID();
        var reservation = fixture.reservation(reservationId, true);
        reservations.save(reservation);
        originalDeparture = reservation.departureId();
    }

    private ReschedulingEpisode open() {
        return ReschedulingEpisode.open(UUID.randomUUID(), fixture.tenant, reservationId, originalDeparture, CANCELLED_AT, NOW);
    }

    @Test
    void aReservationWithoutAnEpisodeIsNotMarked() {
        assertThat(marks.isMarked(fixture.tenant, reservationId)).isFalse();
    }

    @Test
    void aReservationWithAnOpenEpisodeIsMarkedWhetherOrNotAProposalWasSent() {
        ReschedulingEpisode episode = open();
        episodes.save(episode);
        assertThat(marks.isMarked(fixture.tenant, reservationId)).isTrue();

        episodes.save(episode.propose(UUID.randomUUID(), UUID.randomUUID(), 2, admin, CANCELLED_AT.plus(Duration.ofHours(2)), START));
        assertThat(marks.isMarked(fixture.tenant, reservationId)).isTrue();
    }

    @Test
    void theMarkEndsWhenTheProposalIsAccepted() {
        ReschedulingEpisode proposed = open().propose(UUID.randomUUID(), UUID.randomUUID(), 2, admin, CANCELLED_AT.plus(Duration.ofHours(2)), START);
        episodes.save(proposed);
        Instant answeredAt = NOW.minus(Duration.ofHours(1));

        episodes.save(proposed.answer(ProposalAnswer.outside(true, admin, "PHONE", "Called the agency and agreed", answeredAt.minusSeconds(60)), answeredAt));

        assertThat(marks.isMarked(fixture.tenant, reservationId)).isFalse();
    }

    @Test
    void anExpiredEpisodeNoLongerMarksTheReservation() {
        ReschedulingEpisode proposed = open().propose(UUID.randomUUID(), UUID.randomUUID(), 2, admin, CANCELLED_AT.plus(Duration.ofHours(2)), START);
        episodes.save(proposed);

        episodes.save(proposed.expire(proposed.episodeLimit()));

        assertThat(marks.isMarked(fixture.tenant, reservationId)).isFalse();
    }

    @Test
    void anotherTenantAndAnotherReservationAreNeverMarked() {
        episodes.save(open());

        assertThat(marks.isMarked(UUID.randomUUID(), reservationId)).isFalse();
        assertThat(marks.isMarked(fixture.tenant, UUID.randomUUID())).isFalse();
    }
}
