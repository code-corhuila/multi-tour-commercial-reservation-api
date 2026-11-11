package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentDeadlineFreezes;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationRescheduled;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingRecord;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-018 over the real schema (api tasks T-02 to T-06; AC-18.8, AC-18.9, AC-18.12, AC-18.19; db slices rescheduling_record, the hold kind of capacity_allocation and the outbox event): the adapters
// that the internal routes need. The hold of a proposal and the seat of the reservation are different rows of the same allocation table and never mix, the record is appended and never edited, the
// deadlines freeze and resume by reservation with their cause, the due list reads across tenants, and the event of the agreed change reaches the outbox with its closed list of keys. Invented values.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReschedulingAdaptersPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;

    private ReservationPersistenceFixture a;
    private ReservationPersistenceFixture b;
    private JdbcSalidaCapacityHolder holder;
    private JdbcDepartureStateRepository departures;
    private UUID reservationA;
    private UUID proposed;
    private UUID original;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        a = new ReservationPersistenceFixture();
        b = new ReservationPersistenceFixture();
        holder = new JdbcSalidaCapacityHolder(a.jdbc);
        departures = new JdbcDepartureStateRepository(a.jdbc);
        var reservations = new JdbcReservationRepository(a.jdbc, a.cipher, a.transactions, new JdbcReservationCreatedOutbox(a.jdbc));
        reservationA = UUID.randomUUID();
        var reservation = a.reservation(reservationA, true);
        reservations.save(reservation);
        original = reservation.departureId();
        proposed = UUID.randomUUID();
        departures.save(new DepartureState(proposed, a.tenant, a.service, NOW.plus(Duration.ofDays(40)), DepartureStatus.SCHEDULED, 10, 0));
    }

    private int heldSeats() {
        return a.jdbc.queryForObject("SELECT held_seats FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?", Integer.class, a.tenant, proposed);
    }

    private int allocations(String kind) {
        return a.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.capacity_allocation WHERE tenant_id = ? AND reservation_id = ? AND allocation_kind = ? AND released_at IS NULL",
                Integer.class, a.tenant, reservationA, kind);
    }

    @Test
    void theHoldOfAProposalIsItsOwnAllocationAndBecomesTheSeatWhenAccepted() {
        assertThat(holder.holdForRescheduling(a.tenant, reservationA, proposed, 3)).isTrue();
        assertThat(heldSeats()).isEqualTo(3);
        assertThat(allocations("REPROGRAMMING_HOLD")).isEqualTo(1);
        assertThat(allocations("RESERVATION")).as("the hold is not a seat").isZero();

        holder.acceptReschedulingHold(a.tenant, reservationA, proposed);

        assertThat(allocations("REPROGRAMMING_HOLD")).isZero();
        assertThat(allocations("RESERVATION")).isEqualTo(1);
        assertThat(heldSeats()).as("the seats were held when the proposal was sent: nothing is held twice").isEqualTo(3);
    }

    @Test
    void aHoldWithoutRoomChangesNothingAndAReleaseGivesTheSeatsBackOnceAndIsSafeToRepeat() {
        departures.save(new DepartureState(proposed, a.tenant, a.service, NOW.plus(Duration.ofDays(40)), DepartureStatus.SCHEDULED, 2, 0));
        assertThat(holder.holdForRescheduling(a.tenant, reservationA, proposed, 3)).isFalse();
        assertThat(heldSeats()).isZero();
        assertThat(allocations("REPROGRAMMING_HOLD")).isZero();

        departures.save(new DepartureState(proposed, a.tenant, a.service, NOW.plus(Duration.ofDays(40)), DepartureStatus.SCHEDULED, 10, 0));
        assertThat(holder.holdForRescheduling(a.tenant, reservationA, proposed, 3)).isTrue();
        assertThat(holder.releaseReschedulingHold(a.tenant, reservationA)).isEqualTo(3);
        assertThat(holder.releaseReschedulingHold(a.tenant, reservationA)).isZero();
        assertThat(heldSeats()).isZero();
    }

    @Test
    void theRecordIsAppendedOnceAndNeverEdited() {
        var episodes = new JdbcReschedulingRepository(a.jdbc);
        ReschedulingEpisode episode = ReschedulingEpisode.open(UUID.randomUUID(), a.tenant, reservationA, original, NOW.minus(Duration.ofHours(30)), NOW);
        episodes.save(episode);
        var records = new JdbcReschedulingRecords(a.jdbc);
        ReschedulingRecord record = new ReschedulingRecord(UUID.randomUUID(), a.tenant, episode.id(), reservationA, null, proposed, NOW.plus(Duration.ofDays(40)), a.actor, NOW);

        records.save(record);

        assertThat(a.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.rescheduling_record WHERE tenant_id = ? AND rescheduling_episode_id = ?", Integer.class, a.tenant, episode.id())).isEqualTo(1);
        assertThatThrownBy(() -> a.jdbc.update("UPDATE commercial_reservation.rescheduling_record SET approved_by = ? WHERE rescheduling_record_id = ?", UUID.randomUUID(), record.id()))
                .hasStackTraceContaining("permission denied");
    }

    @Test
    void theDeadlinesOfAReservationFreezeAndResumeWithTheirCauseAndNeverTouchAnotherTenant() {
        UUID reservationB = UUID.randomUUID();
        new JdbcReservationRepository(b.jdbc, b.cipher, b.transactions, new JdbcReservationCreatedOutbox(b.jdbc)).save(b.reservation(reservationB, true));
        a.jdbc.update("INSERT INTO commercial_reservation.payment_deadline (payment_deadline_id, tenant_id, reservation_id, deadline_kind, due_at) VALUES (?, ?, ?, 'INITIAL_PAYMENT', ?)",
                UUID.randomUUID(), a.tenant, reservationA, OffsetDateTime.ofInstant(NOW.plus(Duration.ofHours(5)), ZoneOffset.UTC));
        b.jdbc.update("INSERT INTO commercial_reservation.payment_deadline (payment_deadline_id, tenant_id, reservation_id, deadline_kind, due_at) VALUES (?, ?, ?, 'INITIAL_PAYMENT', ?)",
                UUID.randomUUID(), b.tenant, reservationB, OffsetDateTime.ofInstant(NOW.plus(Duration.ofHours(5)), ZoneOffset.UTC));
        var freezes = new JdbcPaymentDeadlineFreezes(a.jdbc);

        freezes.freeze(a.tenant, reservationA, NOW, FreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED);

        var frozen = a.jdbc.queryForMap("SELECT remaining_at_freeze_minutes, freeze_cause FROM commercial_reservation.payment_deadline WHERE tenant_id = ? AND reservation_id = ?", a.tenant, reservationA);
        assertThat(frozen.get("remaining_at_freeze_minutes")).isEqualTo(300);
        assertThat(frozen.get("freeze_cause")).isEqualTo("DEPARTURE_CANCELLED_OR_INTERRUPTED");
        assertThat(b.jdbc.queryForObject("SELECT frozen_at FROM commercial_reservation.payment_deadline WHERE tenant_id = ? AND reservation_id = ?", OffsetDateTime.class, b.tenant, reservationB)).isNull();

        Instant back = NOW.plus(Duration.ofHours(20));
        freezes.resume(a.tenant, reservationA, back, null);

        var resumed = a.jdbc.queryForMap("SELECT frozen_at, due_at FROM commercial_reservation.payment_deadline WHERE tenant_id = ? AND reservation_id = ?", a.tenant, reservationA);
        assertThat(resumed.get("frozen_at")).isNull();
        assertThat(((java.sql.Timestamp) resumed.get("due_at")).toInstant()).isEqualTo(back.plus(Duration.ofHours(5)));
    }

    @Test
    void theDueListReadsAcrossTenantsAndOnlyWhatRanOut() {
        var episodes = new JdbcReschedulingRepository(a.jdbc);
        UUID reservationB = UUID.randomUUID();
        new JdbcReservationRepository(b.jdbc, b.cipher, b.transactions, new JdbcReservationCreatedOutbox(b.jdbc)).save(b.reservation(reservationB, true));
        ReschedulingEpisode late = ReschedulingEpisode.open(UUID.randomUUID(), a.tenant, reservationA, original, NOW.minus(Duration.ofHours(30)), NOW);
        ReschedulingEpisode lateB = ReschedulingEpisode.open(UUID.randomUUID(), b.tenant, reservationB, original, NOW.minus(Duration.ofHours(30)), NOW);
        episodes.save(late);
        episodes.save(lateB);
        UUID reservationC = UUID.randomUUID();
        new JdbcReservationRepository(a.jdbc, a.cipher, a.transactions, new JdbcReservationCreatedOutbox(a.jdbc)).save(a.reservation(reservationC, false));
        episodes.save(ReschedulingEpisode.open(UUID.randomUUID(), a.tenant, reservationC, UUID.randomUUID(), NOW.minus(Duration.ofHours(2)), NOW));

        var due = new JdbcReschedulingQueries(a.jdbc).due(NOW, 100);

        assertThat(due).extracting(d -> d.episodeId()).contains(late.id(), lateB.id());
        assertThat(due).as("an episode still inside its 24 hours is not due").noneMatch(d -> d.reservationId().equals(reservationC));
        assertThat(due).filteredOn(d -> d.episodeId().equals(lateB.id())).singleElement().satisfies(d -> assertThat(d.tenantId()).isEqualTo(b.tenant));
    }

    @Test
    void thePendingProposalsOfADepartureAreFoundByTheirTenant() {
        var episodes = new JdbcReschedulingRepository(a.jdbc);
        ReschedulingEpisode episode = ReschedulingEpisode.open(UUID.randomUUID(), a.tenant, reservationA, original, NOW.minus(Duration.ofHours(5)), NOW)
                .propose(UUID.randomUUID(), proposed, 3, a.actor, NOW.minus(Duration.ofHours(4)), NOW.plus(Duration.ofDays(40)));
        episodes.save(episode);

        assertThat(episodes.findPendingOn(a.tenant, proposed)).extracting(ReschedulingEpisode::id).containsExactly(episode.id());
        assertThat(episodes.findPendingOn(b.tenant, proposed)).isEmpty();
        assertThat(episodes.findPendingOn(a.tenant, UUID.randomUUID())).isEmpty();
    }

    @Test
    void theEventOfTheAgreedChangeReachesTheOutboxWithItsSevenKeysAndTheApprover() {
        UUID recordId = UUID.randomUUID();
        new JdbcPaymentEventOutbox(a.jdbc).append(new ReservationRescheduled(a.tenant, reservationA, recordId, null, original, proposed, NOW.plus(Duration.ofDays(40)), a.actor, NOW));

        var row = a.jdbc.queryForMap("SELECT actor_id, payload::text AS payload FROM commercial_reservation.outbox_event WHERE tenant_id = ? AND event_type = 'ReservationRescheduled' AND reservation_id = ?",
                a.tenant, reservationA);
        assertThat(row.get("actor_id")).isEqualTo(a.actor);
        assertThat((String) row.get("payload")).contains("reschedulingRecordId", "originalSalidaId", "agreedSalidaId", "agreedStartAt", "approvedBy", "linkedReservationId")
                .doesNotContain("evidence", "channel");
    }
}
