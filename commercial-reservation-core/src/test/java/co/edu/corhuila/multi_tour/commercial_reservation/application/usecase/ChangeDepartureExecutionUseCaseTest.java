package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureExecutionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// G-API4-1 (RN-DEX-01 to RN-DEX-04): the start and the end of a departure move its reservations. Every store is a fake; the real statements are proved in
// DepartureExecutionPersistenceTest. Every value is invented.
class ChangeDepartureExecutionUseCaseTest {
    private static final String KEY = "execution-key-0001";
    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    private final UUID tenant = UUID.randomUUID(), salida = UUID.randomUUID();
    private final Store store = new Store();
    private final Keys keys = new Keys();
    private final ChangeDepartureExecutionUseCase useCase = new ChangeDepartureExecutionUseCase(store, store, keys, keys);
    private UUID paid, paidToo, notPaid, cancelled;

    private Reservation reservation(PaymentType type) {
        UUID id = UUID.randomUUID();
        Reservation reservation = Reservation.create(id, tenant, UUID.randomUUID(), salida, UUID.randomUUID(), 2,
                new ReservedService(UUID.randomUUID(), new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(), type,
                PaymentMethod.TRANSFER, NOW);
        store.reservations.put(id, reservation);
        return reservation;
    }

    private void plant(DepartureStatus status) {
        store.status = Optional.of(status);
        Reservation first = reservation(PaymentType.FULL_PAYMENT);
        first.registerCash(UUID.randomUUID(), new Cents(300_000), null, UUID.randomUUID(), NOW);
        Reservation second = reservation(PaymentType.FULL_PAYMENT);
        second.registerCash(UUID.randomUUID(), new Cents(300_000), null, UUID.randomUUID(), NOW);
        Reservation deposit = reservation(PaymentType.DEPOSIT);
        deposit.registerCash(UUID.randomUUID(), new Cents(100_000), new Cents(100_000), UUID.randomUUID(), NOW);
        Reservation dead = reservation(PaymentType.FULL_PAYMENT);
        dead.cancel(CancellationCause.CUSTOMER_REQUEST, UUID.randomUUID(), NOW, 0);
        paid = first.reservationId();
        paidToo = second.reservationId();
        notPaid = deposit.reservationId();
        cancelled = dead.reservationId();
    }

    private ReservationStatus status(UUID id) {
        return store.reservations.get(id).reservationStatus();
    }

    @Test void theStartMovesTheConfirmedFullyPaidOnesAndLeavesTheRest() {
        plant(DepartureStatus.SCHEDULED);
        var done = useCase.start(tenant, salida, KEY);
        assertThat(done.departureId()).isEqualTo(salida);
        assertThat(done.changed()).isEqualTo(2);
        assertThat(done.unchanged()).isEqualTo(2);
        assertThat(status(paid)).isEqualTo(ReservationStatus.IN_EXECUTION);
        assertThat(status(paidToo)).isEqualTo(ReservationStatus.IN_EXECUTION);
        assertThat(status(notPaid)).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(status(cancelled)).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(store.saved).containsExactlyInAnyOrder(paid, paidToo);
    }

    @Test void theEndCompletesTheOnesInExecutionAndLeavesTheRest() {
        plant(DepartureStatus.IN_EXECUTION);
        useCase.start(tenant, salida, KEY);
        var done = useCase.end(tenant, salida, "execution-key-0002");
        assertThat(done.changed()).isEqualTo(2);
        assertThat(done.unchanged()).isEqualTo(2);
        assertThat(status(paid)).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(status(notPaid)).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(status(cancelled)).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test void aRepeatedKeyAnswersTheStoredCountsAndWritesNothing() {
        plant(DepartureStatus.SCHEDULED);
        var first = useCase.start(tenant, salida, KEY);
        store.saved.clear();
        var replay = useCase.start(tenant, salida, KEY);
        assertThat(replay).isEqualTo(first);
        assertThat(store.saved).isEmpty();
        assertThat(replay.changed()).isEqualTo(2);
    }

    @Test void theSameKeyForAnotherDepartureOrTheOtherRouteIsRefused() {
        plant(DepartureStatus.SCHEDULED);
        useCase.start(tenant, salida, KEY);
        assertThatThrownBy(() -> useCase.start(tenant, UUID.randomUUID(), KEY)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> useCase.end(tenant, salida, KEY)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test void anUnknownDepartureIsNotFoundAndLeavesNoKey() {
        assertThatThrownBy(() -> useCase.start(tenant, salida, KEY)).isInstanceOf(DepartureNotFoundException.class);
        assertThatThrownBy(() -> useCase.end(tenant, salida, KEY)).isInstanceOf(DepartureNotFoundException.class);
        assertThat(keys.stored).isEmpty();
    }

    @Test void aFinalDepartureIsRefusedAndNothingChanges() {
        plant(DepartureStatus.FINALIZED);
        assertThatThrownBy(() -> useCase.start(tenant, salida, KEY)).isInstanceOf(BusinessRuleViolation.class);
        plant(DepartureStatus.CANCELLED);
        assertThatThrownBy(() -> useCase.start(tenant, salida, "execution-key-0003")).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> useCase.end(tenant, salida, "execution-key-0004")).isInstanceOf(BusinessRuleViolation.class);
        assertThat(store.saved).isEmpty();
        assertThat(keys.stored).isEmpty();
    }

    @Test void aFailureLeavesEveryReservationAsItWasAndTheKeyUnspent() {
        plant(DepartureStatus.SCHEDULED);
        store.failOn = paidToo;
        assertThatThrownBy(() -> useCase.start(tenant, salida, KEY)).isInstanceOf(IllegalStateException.class);
        // The fake store has no rollback of rows: that is proved over the real database in DepartureExecutionPersistenceTest. The key, which the use case owns, is back.
        assertThat(keys.stored).isEmpty();
    }

    private static final class Store implements DepartureExecutionReader, ReservationRepository {
        final Map<UUID, Reservation> reservations = new LinkedHashMap<>();
        final java.util.ArrayList<UUID> saved = new java.util.ArrayList<>();
        Optional<DepartureStatus> status = Optional.empty();
        UUID failOn;

        @Override public Optional<DepartureStatus> statusOf(UUID tenantId, UUID salidaId) { return status; }
        @Override public List<UUID> reservationIds(UUID tenantId, UUID salidaId) { return List.copyOf(reservations.keySet()); }
        @Override public void save(Reservation reservation) {
            if (reservation.reservationId().equals(failOn)) throw new IllegalStateException("the write failed");
            saved.add(reservation.reservationId());
        }
        @Override public Optional<Reservation> findByTenantAndId(UUID tenantId, UUID reservationId) { return Optional.ofNullable(reservations.get(reservationId)); }
    }

    // The keys and the transaction in one fake: a failed unit of work puts the keys back as they were, as the database does.
    private static final class Keys implements IdempotencyStore, PaymentTransaction {
        Map<String, UUID> stored = new HashMap<>();

        @Override public Optional<UUID> find(UUID tenantId, String key) { return Optional.ofNullable(stored.get(tenantId + key)); }
        @Override public boolean register(UUID tenantId, String key, String resourceType, UUID resourceId) {
            return stored.putIfAbsent(tenantId + key, resourceId) == null;
        }
        @Override public <T> T inTransaction(Supplier<T> work) {
            var before = new HashMap<>(stored);
            try {
                return work.get();
            } catch (RuntimeException failure) {
                stored = before;
                throw failure;
            }
        }
    }
}
