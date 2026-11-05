package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApproveProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingPaymentDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03, T-04, T-06 of HU-COM-010, the core part: RN-07, RN-08, RN-13, RN-14, RN-18, RN-19; AC-10.1 to AC-10.3, AC-10.7 to AC-10.10,
// AC-10.15, AC-10.16, AC-10.20, AC-10.21. The cancellation of HU-COM-011 and the treatment of HU-COM-016 are doubles (DD-5, DD-9);
// the row lock, the final-decision trigger and the idempotency of the decision are adapter work.
class DecidePaymentProofTest {

    private static final UUID TENANT = new UUID(10, 0xb1), ADMIN = new UUID(10, 0xb2), CUSTOMER = new UUID(10, 0xb3), SERVICE = new UUID(10, 0xb4),
            DEPARTURE = new UUID(10, 0xb5), RESERVATION = new UUID(10, 0xb6), FIRST = new UUID(10, 0xb7), SECOND = new UUID(10, 0xb8),
            CASH = new UUID(10, 0xb9), OTHER_TENANT = new UUID(10, 0xba);
    private static final Instant REGISTERED = Instant.parse("2026-10-29T10:00:00Z");
    private static final Instant NOW = REGISTERED.plus(Duration.ofHours(3));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final ReviewFakes review = new ReviewFakes(fakes);
    private final CashFakes cash = new CashFakes();
    private final DecidePaymentProofUseCase decide = new DecidePaymentProofUseCase(fakes, fakes, departures, fakes, fakes, review, review, cash, fakes, fakes,
            Clock.fixed(NOW, ZoneOffset.UTC));

    DecidePaymentProofTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plus(Duration.ofDays(5)), DepartureStatus.SCHEDULED, 10, 2));
    }

    private static PaymentActor admin() {
        return new PaymentActor(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR);
    }

    // Two people, 300.000, a transfer of 100.000 in validation that holds the seats.
    private Reservation planted(PaymentType type, long declared) {
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                type, PaymentMethod.TRANSFER, REGISTERED);
        reservation.registerTransfer(FIRST, new Cents(declared), type == PaymentType.DEPOSIT ? new Cents(100_000) : null, ADMIN, REGISTERED);
        reservation.allocateSeat(REGISTERED);
        reservation.pullEvents();
        fakes.reservations.put(RESERVATION, reservation);
        return reservation;
    }

    private DecidedProof approve(long verified, String reason) {
        return decide.approve(new ApproveProofCommand(admin(), FIRST, verified, reason));
    }

    private static List<String> names(List<? extends Object> events) {
        return events.stream().map(e -> e.getClass().getSimpleName()).toList();
    }

    @Test
    void withoutThePermissionNoDecisionIsMadeForEveryRole() {
        planted(PaymentType.DEPOSIT, 100_000);
        List<PaymentActor> refused = List.of(new PaymentActor(TENANT, ADMIN, Role.OPERATIONAL_COLLABORATOR),
                new PaymentActor(TENANT, ADMIN, Role.OPERATIONAL_COLLABORATOR, Set.of("OTHER_PERMISSION")),
                new PaymentActor(TENANT, ADMIN, Role.MANAGER, Set.of("VALIDATE_PAYMENT_PROOFS")),
                new PaymentActor(TENANT, ADMIN, Role.ACCOUNTANT, Set.of("VALIDATE_PAYMENT_PROOFS")),
                new PaymentActor(TENANT, ADMIN, Role.ANALYST, Set.of("VALIDATE_PAYMENT_PROOFS")),
                new PaymentActor(TENANT, ADMIN, Role.FINAL_CUSTOMER, Set.of("VALIDATE_PAYMENT_PROOFS")));

        for (PaymentActor actor : refused) {
            assertThatThrownBy(() -> decide.approve(new ApproveProofCommand(actor, FIRST, 100_000L, null)))
                    .isInstanceOf(PaymentAccessDeniedException.class);
            assertThatThrownBy(() -> decide.reject(new RejectProofCommand(actor, FIRST, "no"))).isInstanceOf(PaymentAccessDeniedException.class);
        }

        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.reservations.get(RESERVATION).attempts().get(0).validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
    }

    @Test
    void theAdministratorByProfileAndTheCollaboratorWithThePermissionDecide() {
        planted(PaymentType.DEPOSIT, 100_000);
        PaymentActor collaborator = new PaymentActor(TENANT, ADMIN, Role.OPERATIONAL_COLLABORATOR, Set.of("VALIDATE_PAYMENT_PROOFS"));

        DecidedProof decided = decide.approve(new ApproveProofCommand(collaborator, FIRST, 100_000L, null));

        assertThat(decided.attempt().validationStatus()).isEqualTo(ValidationStatus.APPROVED);
        planted(PaymentType.DEPOSIT, 100_000);
        assertThat(decide.reject(new RejectProofCommand(admin(), FIRST, "illegible")).attempt().validationStatus())
                .isEqualTo(ValidationStatus.REJECTED);
    }

    @Test
    void minimumDepositApprovedConfirmsWithDepositReceivedAndEmitsTheEventsInOneTransaction() {
        planted(PaymentType.DEPOSIT, 100_000);

        DecidedProof decided = approve(100_000, null);

        Reservation stored = fakes.reservations.get(RESERVATION);
        assertThat(stored.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(stored.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
        assertThat(stored.pendingBalance()).isEqualTo(new Cents(200_000));
        assertThat(decided.reservation()).isSameAs(stored);
        assertThat(names(fakes.events)).containsExactly("PaymentValidated", "ReservationConfirmed");
        assertThat(fakes.saved).hasSize(1);
        assertThat(review.cancelled).isEmpty();
        assertThat(cash.excess).isEmpty();
    }

    @Test
    void theExcessOverTheBalanceIsHandedToTheCreditBalance() {
        Reservation reservation = planted(PaymentType.FULL_PAYMENT, 200_000);
        reservation.registerCash(CASH, new Cents(150_000), null, ADMIN, REGISTERED);
        reservation.pullEvents();

        DecidedProof decided = approve(200_000, null);

        assertThat(decided.applied()).isEqualTo(new Cents(150_000));
        assertThat(decided.excess()).isEqualTo(new Cents(50_000));
        assertThat(cash.excess).containsExactly(new Cents(50_000));
        assertThat(fakes.reservations.get(RESERVATION).paymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
    }

    @Test
    void rejectingTheFirstPaymentReleasesTheHeldSeatsInTheSameTransaction() {
        planted(PaymentType.DEPOSIT, 100_000);

        DecidedProof decided = decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));

        assertThat(names(fakes.events)).containsExactly("PaymentRejected", "CapacityReleased");
        assertThat(fakes.heldSeats(DEPARTURE)).isZero();
        assertThat(decided.reservation().seatHeld()).isFalse();
        assertThat(decided.reservation().pendingBalance()).isEqualTo(new Cents(300_000));
        assertThat(review.cancelled).isEmpty();
    }

    @Test
    void rejectingWhileAnotherProofIsUnderReviewKeepsTheSeats() {
        Reservation reservation = planted(PaymentType.FULL_PAYMENT, 100_000);
        reservation.registerTransfer(SECOND, new Cents(50_000), null, ADMIN, REGISTERED);
        reservation.pullEvents();

        decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));

        assertThat(names(fakes.events)).containsExactly("PaymentRejected");
        assertThat(fakes.releaseCalls).isZero();
        assertThat(fakes.heldSeats(DEPARTURE)).isEqualTo(2);
    }

    @Test
    void approvingOnACancelledReservationNeverAddsToValidatedValuesNorCallsTheCreditBalance() {
        Reservation reservation = planted(PaymentType.DEPOSIT, 100_000);
        reservation.restoredAs(ReservationStatus.CANCELLED, PaymentStatus.NO_PAYMENT, new Cents(300_000), false, reservation.attempts());

        DecidedProof decided = approve(100_000, null);

        assertThat(decided.reservation().reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(decided.reservation().pendingBalance()).isEqualTo(new Cents(300_000));
        assertThat(review.values).containsExactly("APPROVED_AFTER_CANCELLATION:100000");
        assertThat(cash.excess).isEmpty();
        assertThat(review.cancelled).isEmpty();
    }

    @Test
    void rejectingAfterTheDeadlineCancelsTheReservationAndReleasesTheSeat() {
        planted(PaymentType.DEPOSIT, 100_000);
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW));

        DecidedProof decided = decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));

        assertThat(review.cancelled).containsExactly(RESERVATION);
        assertThat(decided.reservation().reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(fakes.heldSeats(DEPARTURE)).isZero();
        assertThat(names(fakes.events)).containsExactly("PaymentRejected", "CapacityReleased");
    }

    @Test
    void rejectingAfterTheBalanceDeadlineCancelsToo() {
        Reservation reservation = planted(PaymentType.DEPOSIT, 100_000);
        approve(100_000, null);
        reservation.registerTransfer(SECOND, new Cents(50_000), new Cents(100_000), ADMIN, REGISTERED);
        reservation.pullEvents();
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.BALANCE, NOW.minusSeconds(1)));
        fakes.events.clear();

        decide.reject(new RejectProofCommand(admin(), SECOND, "illegible"));

        assertThat(review.cancelled).containsExactly(RESERVATION);
        assertThat(names(fakes.events)).containsExactly("PaymentRejected", "CapacityReleased");
    }

    @Test
    void approvingLateForTooLittleCancelsAndHandsTheValuesToPendingTreatment() {
        planted(PaymentType.DEPOSIT, 100_000);
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.minusSeconds(60)));

        DecidedProof decided = approve(60_000, "only 60.000 arrived");

        assertThat(review.cancelled).containsExactly(RESERVATION);
        assertThat(decided.reservation().reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(review.values).containsExactly("PENDING_TREATMENT_AFTER_EXPIRY:60000");
        assertThat(fakes.heldSeats(DEPARTURE)).isZero();
        assertThat(names(fakes.events)).containsExactly("PaymentValidated", "CapacityReleased");
    }

    @Test
    void approvingLateWithEnoughConfirmsAsUsualAndCreatesNoCancellation() {
        planted(PaymentType.DEPOSIT, 100_000);
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.minusSeconds(60)));

        approve(100_000, null);

        assertThat(review.cancelled).isEmpty();
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(fakes.heldSeats(DEPARTURE)).isEqualTo(2);
    }

    @Test
    void aDecidedProofCannotBeDecidedAgainAndTheStoredDecisionIsUntouched() {
        planted(PaymentType.DEPOSIT, 100_000);
        decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));
        int events = fakes.events.size();

        assertThatThrownBy(() -> approve(100_000, null)).isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(fakes.events).hasSize(events);
        assertThat(fakes.reservations.get(RESERVATION).attempts().get(0).decisionReason()).isEqualTo("illegible");
    }

    @Test
    void aMissingAmountOrReasonIsNamedAndNothingChanges() {
        planted(PaymentType.DEPOSIT, 100_000);

        assertThatThrownBy(() -> decide.approve(new ApproveProofCommand(admin(), FIRST, null, null))).isInstanceOfSatisfying(
                MissingPaymentDataException.class, e -> assertThat(e.fields()).containsExactly("verifiedAmountCents"));
        assertThatThrownBy(() -> decide.reject(new RejectProofCommand(admin(), FIRST, " "))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("reason"));
        assertThatThrownBy(() -> approve(0, "x")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("verifiedAmountCents"));

        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void aProofOfAnotherTenantOrUnknownAnswersNotFound() {
        planted(PaymentType.DEPOSIT, 100_000);
        PaymentActor stranger = new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR);

        assertThatThrownBy(() -> decide.approve(new ApproveProofCommand(stranger, FIRST, 100_000L, null)))
                .isInstanceOf(PaymentProofNotFoundException.class);
        assertThatThrownBy(() -> decide.reject(new RejectProofCommand(admin(), new UUID(10, 0xff), "no")))
                .isInstanceOf(PaymentProofNotFoundException.class);
    }

    private PaymentDeadline initialDeadline(Instant dueAt, Integer extensionHours, Instant frozenAt) {
        return new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, dueAt, extensionHours, extensionHours == null ? null : REGISTERED,
                extensionHours == null ? null : ADMIN, frozenAt, frozenAt == null ? null : 90);
    }

    // RN-02, RN-17 (AC-10.20, AC-10.21): the decision judges the deadline in force, as the expiry job and the payment preconditions do.
    @Test
    void rejectingInsideTheExtensionDoesNotCancelForNonPayment() {
        planted(PaymentType.DEPOSIT, 100_000);
        fakes.deadlines.put(RESERVATION, initialDeadline(NOW.minusSeconds(60), 2, null));

        DecidedProof decided = decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));

        assertThat(review.cancelled).isEmpty();
        assertThat(decided.reservation().reservationStatus()).isNotEqualTo(ReservationStatus.CANCELLED);
        assertThat(names(fakes.events)).containsExactly("PaymentRejected", "CapacityReleased");
    }

    @Test
    void approvingInsideTheExtensionForTooLittleDoesNotCancel() {
        planted(PaymentType.DEPOSIT, 100_000);
        fakes.deadlines.put(RESERVATION, initialDeadline(NOW.minusSeconds(60), 2, null));

        DecidedProof decided = approve(60_000, "only 60.000 arrived");

        assertThat(review.cancelled).isEmpty();
        assertThat(review.values).isEmpty();
        assertThat(decided.reservation().reservationStatus()).isNotEqualTo(ReservationStatus.CANCELLED);
        assertThat(fakes.heldSeats(DEPARTURE)).isEqualTo(2);
    }

    @Test
    void afterTheExtendedInstantTheDecisionCancelsAsBefore() {
        planted(PaymentType.DEPOSIT, 100_000);
        fakes.deadlines.put(RESERVATION, initialDeadline(NOW.minus(Duration.ofHours(3)), 2, null));

        decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));

        assertThat(review.cancelled).containsExactly(RESERVATION);
        assertThat(fakes.heldSeats(DEPARTURE)).isZero();
    }

    @Test
    void aFrozenDeadlineNeverCancelsOnApproval() {
        planted(PaymentType.DEPOSIT, 100_000);
        fakes.deadlines.put(RESERVATION, initialDeadline(NOW.minus(Duration.ofHours(5)), null, NOW.minus(Duration.ofHours(4))));

        approve(60_000, "only 60.000 arrived");

        assertThat(review.cancelled).isEmpty();
        assertThat(review.values).isEmpty();
        assertThat(fakes.heldSeats(DEPARTURE)).isEqualTo(2);
    }

    @Test
    void aFrozenDeadlineNeverCancelsOnRejection() {
        planted(PaymentType.DEPOSIT, 100_000);
        fakes.deadlines.put(RESERVATION, initialDeadline(NOW.minus(Duration.ofHours(5)), null, NOW.minus(Duration.ofHours(4))));

        decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));

        assertThat(review.cancelled).isEmpty();
    }

    @Test
    void theExtensionNeverGoesPastTheDepartureStart() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.minus(Duration.ofMinutes(10)), DepartureStatus.SCHEDULED, 10, 2));
        planted(PaymentType.DEPOSIT, 100_000);
        fakes.deadlines.put(RESERVATION, initialDeadline(NOW.minus(Duration.ofHours(3)), 10, null));

        decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));

        assertThat(review.cancelled).containsExactly(RESERVATION);
    }
}
