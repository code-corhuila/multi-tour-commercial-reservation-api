package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-01 and T-06 (domain part) of HU-COM-009: RN-01, RN-02, RN-08, RN-10; AC-09.1, AC-09.2, AC-09.9, AC-09.11, AC-09.13, AC-09.14.
class ReservationPaymentTest {

    private static final UUID ID = new UUID(9, 1), TENANT = new UUID(9, 2), CUSTOMER = new UUID(9, 3), DEPARTURE = new UUID(9, 4),
            SERVICE = new UUID(9, 5), CREATOR = new UUID(9, 6), ATTEMPT = new UUID(9, 7), OPERATOR = new UUID(9, 8);
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final Cents MIN = new Cents(100_000);

    // Two people at 100.000 plus 50.000 of transport each: 300.000, no discount.
    private static Reservation born(PaymentType type) {
        return Reservation.create(ID, TENANT, CUSTOMER, DEPARTURE, CREATOR, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                type, PaymentMethod.TRANSFER, NOW);
    }

    private static Cents minimumFor(PaymentType type) {
        return type == PaymentType.DEPOSIT ? MIN : null;
    }

    private static PaymentAttempt transfer(Reservation reservation, long amount) {
        return reservation.registerTransfer(ATTEMPT, new Cents(amount), minimumFor(reservation.expectedPaymentType()), OPERATOR, NOW);
    }

    @Test
    void aTransferIsRegisteredInValidationAndChangesNothingElse() {
        Reservation reservation = born(PaymentType.DEPOSIT);

        PaymentAttempt attempt = transfer(reservation, 100_000);

        assertThat(attempt.validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
        assertThat(attempt.method()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(attempt.amount()).isEqualTo(new Cents(100_000));
        assertThat(attempt.paymentStatusSnapshot()).isEqualTo(PaymentStatus.NO_PAYMENT);
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.NO_PAYMENT);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(300_000));
        assertThat(reservation.attempts()).containsExactly(attempt);
    }

    @Test
    void theAttemptRecordsTheUserAndTheMoment() {
        PaymentAttempt attempt = transfer(born(PaymentType.DEPOSIT), 100_000);

        assertThat(attempt.attemptId()).isEqualTo(ATTEMPT);
        assertThat(attempt.registeredBy()).isEqualTo(OPERATOR);
        assertThat(attempt.registeredAt()).isEqualTo(NOW);
    }

    @Test
    void aTransferInValidationEmitsOnlyTheEvidenceEventAndNothingOfCash() {
        Reservation reservation = born(PaymentType.DEPOSIT);
        transfer(reservation, 100_000);

        List<PaymentEvent> events = reservation.pullEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOfSatisfying(PaymentEvidenceRegistered.class, e -> {
            assertThat(e.attemptId()).isEqualTo(ATTEMPT);
            assertThat(e.modality()).isEqualTo(PaymentMethod.TRANSFER);
            assertThat(e.registeredAmount()).isEqualTo(new Cents(100_000));
            assertThat(e.validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
            assertThat(e.reservationId()).isEqualTo(ID);
        });
        assertThat(reservation.pullEvents()).isEmpty();
    }

    @Test
    void anAmountThatIsNotPositiveOrAboveTheBalanceIsRefusedNamingTheField() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);

        assertThatThrownBy(() -> transfer(reservation, 0)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("amountCents"));
        assertThatThrownBy(() -> transfer(reservation, 300_001)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("amountCents"));
        assertThatThrownBy(() -> reservation.registerTransfer(ATTEMPT, null, null, OPERATOR, NOW)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.field()).isEqualTo("amountCents"));
        assertThat(reservation.attempts()).isEmpty();
    }

    @Test
    void aDepositBelowTheMinimumIsRefusedOnTheFirstPaymentButAPartialFullPaymentIsAccepted() {
        assertThatThrownBy(() -> transfer(born(PaymentType.DEPOSIT), 99_999)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("amountCents"));

        Reservation full = born(PaymentType.FULL_PAYMENT);
        assertThat(transfer(full, 99_999).validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
    }

    @Test
    void cashCountsAtOnceAndConfirmsWhenTheDepositIsMet() {
        Reservation reservation = born(PaymentType.DEPOSIT);

        CashReceipt receipt = reservation.registerCash(ATTEMPT, new Cents(100_000), MIN, OPERATOR, NOW);

        assertThat(receipt.attempt().validationStatus()).isNull();
        assertThat(receipt.attempt().method()).isEqualTo(PaymentMethod.CASH);
        assertThat(receipt.applied()).isEqualTo(new Cents(100_000));
        assertThat(receipt.excess()).isEqualTo(new Cents(0));
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(200_000));
        assertThat(reservation.pullEvents()).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("PaymentEvidenceRegistered", "PaymentValidated", "ReservationConfirmed");
    }

    @Test
    void cashBelowTheTotalOfAFullPaymentStaysPartialAndPendingWithoutConfirmation() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);

        reservation.registerCash(ATTEMPT, new Cents(120_000), null, OPERATOR, NOW);

        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(180_000));
        assertThat(reservation.pullEvents()).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("PaymentEvidenceRegistered", "PaymentValidated");
    }

    @Test
    void cashAboveTheBalanceAppliesTheBalanceAndReturnsTheExcess() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);

        CashReceipt receipt = reservation.registerCash(ATTEMPT, new Cents(350_000), null, OPERATOR, NOW);

        assertThat(receipt.applied()).isEqualTo(new Cents(300_000));
        assertThat(receipt.excess()).isEqualTo(new Cents(50_000));
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(0));
    }

    @Test
    void cashBelowTheMinimumOfTheFirstDepositIsRefused() {
        assertThatThrownBy(() -> born(PaymentType.DEPOSIT).registerCash(ATTEMPT, new Cents(50_000), MIN, OPERATOR, NOW))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("amountCents"));
    }

    @Test
    void aClosedOrSettledReservationAcceptsNoPayment() {
        Reservation born = born(PaymentType.FULL_PAYMENT);
        for (ReservationStatus closed : List.of(ReservationStatus.CANCELLED, ReservationStatus.IN_EXECUTION, ReservationStatus.COMPLETED)) {
            Reservation reservation = born.restoredAs(closed, PaymentStatus.NO_PAYMENT, new Cents(300_000), false, List.of());
            assertThatThrownBy(() -> transfer(reservation, 100_000)).isInstanceOf(BusinessRuleViolation.class);
        }
        Reservation settled = born.restoredAs(ReservationStatus.CONFIRMED, PaymentStatus.FULLY_PAID, new Cents(0), true, List.of());
        assertThatThrownBy(() -> settled.registerCash(ATTEMPT, new Cents(1), null, OPERATOR, NOW)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void aConfirmedReservationTakesABalancePaymentWithoutTheMinimumAndEndsFullyPaid() {
        Reservation reservation = born(PaymentType.DEPOSIT).restoredAs(ReservationStatus.CONFIRMED, PaymentStatus.DEPOSIT_RECEIVED,
                new Cents(200_000), true, List.of());

        assertThat(transfer(reservation, 50_000).validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
        reservation.registerCash(UUID.randomUUID(), new Cents(200_000), MIN, OPERATOR, NOW);

        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(0));
        assertThat(reservation.pullEvents()).extracting(e -> e.getClass().getSimpleName())
                .doesNotContain("ReservationConfirmed");
    }

    @Test
    void allocatingTheSeatMarksItHeldAndEmitsCapacityAllocatedOnce() {
        Reservation reservation = born(PaymentType.DEPOSIT);

        reservation.allocateSeat(NOW);

        assertThat(reservation.seatHeld()).isTrue();
        assertThat(reservation.pullEvents()).singleElement().isInstanceOfSatisfying(CapacityAllocated.class, e -> {
            assertThat(e.salidaId()).isEqualTo(DEPARTURE);
            assertThat(e.serviceId()).isEqualTo(SERVICE);
            assertThat(e.allocationKind()).isEqualTo("RESERVATION");
        });
        assertThatThrownBy(() -> reservation.allocateSeat(NOW)).isInstanceOf(BusinessRuleViolation.class);
    }
}
