package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EmergencyOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoRefundDueException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PersonalEmergency;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// Spec 024 (-api), T-01: the personal emergency that operations records. The refund is determined by the service (spec 026), the reservation never changes, and the
// event is marked as received in the same transaction as the refund. Doubles only; no tenant status is consulted, so an inactive tenant applies it too (AC-24.10).
class PersonalEmergencyUseCaseTest {

    private static final UUID TENANT = new UUID(24, 1), SALIDA = new UUID(24, 2), RESERVATION = new UUID(24, 3), SERVICE = new UUID(24, 4);
    private static final Instant AT = Instant.parse("2026-11-12T15:00:00Z");

    private final Map<UUID, Reservation> reservations = new LinkedHashMap<>();
    private final Map<UUID, RefundAuthorization> refunds = new LinkedHashMap<>();
    private final Set<UUID> processed = new HashSet<>();
    private final List<String> determined = new ArrayList<>();
    private int depth;
    private boolean refundIsZero;
    private RuntimeException determineFailure;

    private final PaymentTransaction transaction = new PaymentTransaction() {
        @Override public <T> T inTransaction(Supplier<T> work) {
            depth++;
            try { return work.get(); } finally { depth--; }
        }
    };
    private final ProcessedEvents marks = new ProcessedEvents() {
        @Override public boolean wasProcessed(UUID eventId) { return processed.contains(eventId); }
        @Override public void record(UUID eventId) {
            if (depth == 0) throw new IllegalStateException("the mark was written outside the transaction");
            processed.add(eventId);
        }
    };
    private final ReservationRepository repository = new ReservationRepository() {
        @Override public void save(Reservation reservation) { throw new AssertionError("a personal emergency never changes the reservation"); }
        @Override public Optional<Reservation> findByTenantAndId(UUID tenantId, UUID reservationId) {
            return Optional.ofNullable(reservations.get(reservationId)).filter(r -> r.tenantId().equals(tenantId));
        }
    };
    private final RefundAuthorizationRepository refundRepository = new RefundAuthorizationRepository() {
        @Override public Optional<RefundAuthorization> find(UUID tenantId, UUID id) { return Optional.ofNullable(refunds.get(id)); }
        @Override public Optional<RefundAuthorization> findByDecision(UUID tenantId, UUID decisionId) { return Optional.empty(); }
        @Override public Optional<RefundAuthorization> findByReservationAndOrigin(UUID tenantId, UUID reservationId, RefundOrigin origin) {
            return refunds.values().stream().filter(r -> r.reservationId().equals(reservationId) && r.origin() == origin).findFirst();
        }
        @Override public void save(RefundAuthorization refund) { refunds.put(refund.id(), refund); }
    };
    private final DetermineRefund determine = (tenantId, reservationId, origin, affected, occurredAt) -> {
        if (depth == 0) throw new IllegalStateException("the refund was determined outside the transaction");
        if (determineFailure != null) throw determineFailure;
        if (refundIsZero) throw new NoRefundDueException("the policy keeps the whole share");
        determined.add(reservationId + "/" + origin + "/" + affected + "@" + occurredAt);
        RefundAuthorization refund = RefundAuthorization.determine(UUID.randomUUID(), tenantId, reservationId, origin, new Cents(25_000L * affected), AT, AT.plus(Duration.ofDays(7)));
        refunds.put(refund.id(), refund);
        return refund;
    };
    private final PersonalEmergencyUseCase useCase = new PersonalEmergencyUseCase(marks, repository, refundRepository, determine, transaction);

    PersonalEmergencyUseCaseTest() {
        plant(ReservationStatus.IN_EXECUTION);
    }

    private void plant(ReservationStatus status) {
        Reservation reservation = Reservation.create(RESERVATION, TENANT, UUID.randomUUID(), SALIDA, UUID.randomUUID(), 3,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(450_000), new Cents(450_000), List.of(), PaymentType.FULL_PAYMENT,
                PaymentMethod.TRANSFER, AT.minus(Duration.ofDays(30)));
        reservation.restoredAs(status, PaymentStatus.FULLY_PAID, new Cents(0), false, List.of());
        reservations.put(RESERVATION, reservation);
    }

    private PersonalEmergency event(UUID eventId, int affected) {
        return new PersonalEmergency(eventId, TENANT, SALIDA, RESERVATION, affected, AT);
    }

    @Test
    void determinesAnAutomaticRefundForTheAffectedTravelers() {
        UUID eventId = UUID.randomUUID();

        EmergencyOutcome outcome = useCase.apply(event(eventId, 2));

        assertThat(outcome).isEqualTo(EmergencyOutcome.REFUND_DETERMINED);
        assertThat(determined).containsExactly(RESERVATION + "/PERSONAL_EMERGENCY_DURING_EXECUTION/2@" + AT);
        assertThat(processed).containsExactly(eventId);
        assertThat(reservations.get(RESERVATION).reservationStatus()).as("the reservation is not cancelled and keeps its status").isEqualTo(ReservationStatus.IN_EXECUTION);
    }

    @Test
    void createsNoRefundWhenThePolicyReturnsZero() {
        refundIsZero = true;
        UUID eventId = UUID.randomUUID();

        assertThat(useCase.apply(event(eventId, 1))).isEqualTo(EmergencyOutcome.NO_REFUND);

        assertThat(refunds).isEmpty();
        assertThat(processed).as("a zero result is a normal result: the event is marked").containsExactly(eventId);
        assertThat(reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.IN_EXECUTION);
    }

    @Test
    void answers200ForARedelivery() {
        UUID eventId = UUID.randomUUID();
        useCase.apply(event(eventId, 1));

        assertThat(useCase.apply(event(eventId, 1))).isEqualTo(EmergencyOutcome.DUPLICATE);

        assertThat(determined).hasSize(1);
    }

    @Test
    void createsOnlyOneRefundPerReservationForASecondEvent() {
        useCase.apply(event(UUID.randomUUID(), 1));
        UUID second = UUID.randomUUID();

        assertThat(useCase.apply(event(second, 2))).isEqualTo(EmergencyOutcome.ALREADY_DETERMINED);

        assertThat(refunds).hasSize(1);
        assertThat(determined).hasSize(1);
        assertThat(processed).as("the second event is marked too").contains(second);
    }

    @Test
    void ignoresACancelledReservation() {
        reservations.get(RESERVATION).cancel(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER, null, AT, 0);
        UUID eventId = UUID.randomUUID();

        assertThat(useCase.apply(event(eventId, 1))).isEqualTo(EmergencyOutcome.IGNORED_CANCELLED);

        assertThat(refunds).isEmpty();
        assertThat(processed).containsExactly(eventId);
    }

    @Test
    void leavesAnUnknownReservationUnmarked() {
        reservations.clear();
        UUID eventId = UUID.randomUUID();

        assertThat(useCase.apply(event(eventId, 1))).isEqualTo(EmergencyOutcome.UNKNOWN_RESERVATION);

        assertThat(processed).isEmpty();
    }

    @Test
    void refusesAnAffectedNumberAbovePeopleAndLeavesTheEventUnmarked() {
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.apply(event(eventId, 4))).isInstanceOf(DomainException.class).extracting("field").isEqualTo("affected");

        assertThat(processed).isEmpty();
        assertThat(refunds).isEmpty();
    }

    @Test
    void aFailureOfTheDeterminationLeavesTheEventUnmarked() {
        determineFailure = new IllegalStateException("the refund could not be written");
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.apply(event(eventId, 1))).isInstanceOf(IllegalStateException.class);

        assertThat(processed).as("refund and mark commit together: neither survives").isEmpty();
        assertThat(refunds).isEmpty();
    }

    @Test
    void appliesTheEventForAnInactiveTenant() {
        // No tenant status is a dependency of the use case: a fact of the past cannot be refused (AC-24.10).
        assertThat(useCase.apply(event(UUID.randomUUID(), 1))).isEqualTo(EmergencyOutcome.REFUND_DETERMINED);
    }

    @Test
    void anEventOfAnotherTenantFindsNoReservation() {
        PersonalEmergency foreign = new PersonalEmergency(UUID.randomUUID(), new UUID(24, 99), SALIDA, RESERVATION, 1, AT);

        assertThat(useCase.apply(foreign)).isEqualTo(EmergencyOutcome.UNKNOWN_RESERVATION);
    }
}
