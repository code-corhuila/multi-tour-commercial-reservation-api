package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-007 reads and idempotent creation over the real schema (api tasks T-07, T-09): every value below is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReservationReadPersistenceTest {
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository repository;
    private JdbcReservationReader reader;
    private JdbcIdempotencyKeys keys;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        repository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions,
                new JdbcReservationCreatedOutbox(fixture.jdbc));
        reader = new JdbcReservationReader(fixture.jdbc, fixture.cipher);
        keys = new JdbcIdempotencyKeys(fixture.jdbc);
    }

    @Test
    void theReservationIsReadBackAsItWasCreatedCompanionsIncluded() {
        UUID id = UUID.randomUUID();
        Reservation saved = fixture.reservation(id, true, fixture.plainCompanion(), fixture.riskyCompanion());
        repository.save(saved);

        Reservation loaded = reader.findAsCreated(fixture.tenant, id).orElseThrow();

        assertThat(loaded.reservationId()).isEqualTo(id);
        assertThat(loaded.customerId()).isEqualTo(saved.customerId());
        assertThat(loaded.departureId()).isEqualTo(saved.departureId());
        assertThat(loaded.createdBy()).isEqualTo(saved.createdBy());
        assertThat(loaded.people()).isEqualTo(3);
        assertThat(loaded.service()).isEqualTo(saved.service());
        assertThat(loaded.projectedValue()).isEqualTo(saved.projectedValue());
        assertThat(loaded.finalValue()).isEqualTo(saved.finalValue());
        assertThat(loaded.fixedDiscounts()).isEqualTo(saved.fixedDiscounts());
        assertThat(loaded.expectedPaymentType()).isEqualTo(saved.expectedPaymentType());
        assertThat(loaded.expectedPaymentMethod()).isEqualTo(saved.expectedPaymentMethod());
        assertThat(loaded.createdAt()).isEqualTo(saved.createdAt());
        assertThat(loaded.companions()).containsExactlyInAnyOrderElementsOf(saved.companions());
    }

    @Test
    void aReservationOfAnotherTenantIsNotFound() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));

        assertThat(reader.findAsCreated(UUID.randomUUID(), id)).isEmpty();
        assertThat(reader.findByTenantAndId(UUID.randomUUID(), id)).isEmpty();
        assertThat(reader.findAsCreated(fixture.tenant, UUID.randomUUID())).isEmpty();
    }

    @Test
    void theSnapshotHasTheSubtotalLeftByTheFixedLinesAndCountsOnlyTheLinesThatTookSomething() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));

        ReservationSnapshot snapshot = reader.findByTenantAndId(fixture.tenant, id).orElseThrow();

        assertThat(snapshot.tenantId()).isEqualTo(fixture.tenant);
        assertThat(snapshot.reservationId()).isEqualTo(id);
        assertThat(snapshot.state()).isEqualTo(ReservationSnapshot.State.PENDING_PAYMENT);
        assertThat(snapshot.subtotal()).isEqualTo(new Cents(324_000));
        assertThat(snapshot.paid()).isEqualTo(new Cents(0));
        assertThat(snapshot.appliedDiscounts()).isEqualTo(1);
    }

    @Test
    void theAdditionalDiscountCountsAndTheStatesAreMapped() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));
        fixture.jdbc.update("INSERT INTO commercial_reservation.reservation_additional_discount (additional_discount_id, "
                + "tenant_id, reservation_id, discount_percentage_bp, reason, applied_by, applied_at, previous_value_cents, "
                + "amount_cents) VALUES (?, ?, ?, 500, 'Fidelidad', ?, ?, 324000, 16200)", UUID.randomUUID(), fixture.tenant,
                id, fixture.actor, OffsetDateTime.ofInstant(ReservationPersistenceFixture.NOW, ZoneOffset.UTC));

        assertThat(reader.findByTenantAndId(fixture.tenant, id).orElseThrow().appliedDiscounts()).isEqualTo(2);
        setStatus(id, "CONFIRMED");
        assertThat(reader.findByTenantAndId(fixture.tenant, id).orElseThrow().state())
                .isEqualTo(ReservationSnapshot.State.CONFIRMED);
        setStatus(id, "CANCELLED");
        assertThat(reader.findByTenantAndId(fixture.tenant, id).orElseThrow().state())
                .isEqualTo(ReservationSnapshot.State.CANCELLED);
        setStatus(id, "COMPLETED");
        assertThat(reader.findByTenantAndId(fixture.tenant, id).orElseThrow().state())
                .isEqualTo(ReservationSnapshot.State.OTHER);
    }

    @Test
    void theKeyIsWrittenWithTheReservationAndARepeatedKeyLeavesOneReservationAndOneEvent() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(create(first, "key-aaaa-0001")).isTrue();
        assertThat(create(second, "key-aaaa-0001")).isFalse();

        assertThat(keys.findResourceId(fixture.tenant, "key-aaaa-0001")).contains(first);
        assertThat(fixture.count("reservation", first)).isEqualTo(1);
        assertThat(fixture.count("outbox_event", first)).isEqualTo(1);
        assertNothingStored(second);
        assertThat(reader.findAsCreated(fixture.tenant, keys.findResourceId(fixture.tenant, "key-aaaa-0001").orElseThrow()))
                .isPresent();
    }

    @Test
    void twoParallelCreationsWithTheSameKeyLeaveOneReservationAndOneEvent() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = pool.submit(() -> { start.await(); return create(first, "key-bbbb-0002"); });
            Future<Boolean> two = pool.submit(() -> { start.await(); return create(second, "key-bbbb-0002"); });
            start.countDown();
            assertThat(java.util.List.of(one.get(), two.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        UUID winner = keys.findResourceId(fixture.tenant, "key-bbbb-0002").orElseThrow();
        UUID loser = winner.equals(first) ? second : first;
        assertThat(fixture.count("reservation", winner)).isEqualTo(1);
        assertThat(fixture.count("outbox_event", winner)).isEqualTo(1);
        assertNothingStored(loser);
    }

    // The same shape as the customer registration: the reservation and the key in one transaction, discarded when the key lost.
    private boolean create(UUID id, String key) {
        return fixture.transactions.execute(status -> {
            repository.save(fixture.reservation(id, true, fixture.plainCompanion()));
            if (keys.tryRegister(fixture.tenant, key, "reservation", id)) return true;
            status.setRollbackOnly();
            return false;
        });
    }

    private void setStatus(UUID id, String status) {
        fixture.jdbc.update("UPDATE commercial_reservation.reservation SET reservation_status = ? WHERE reservation_id = ?",
                status, id);
    }

    private void assertNothingStored(UUID id) {
        for (String table : new String[] {"reservation", "reserved_service", "reservation_fixed_discount",
                "reservation_companion", "outbox_event"}) {
            assertThat(fixture.count(table, id)).as(table).isZero();
        }
    }

    private static boolean present(String name) {
        return System.getenv(name) != null;
    }
}
