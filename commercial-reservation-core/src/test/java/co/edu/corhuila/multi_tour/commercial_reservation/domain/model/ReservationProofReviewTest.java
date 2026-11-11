package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-01 to T-03 and T-06 (domain part) of HU-COM-010: RN-01 to RN-05, RN-08 to RN-10, RN-13, RN-14; AC-10.1 to AC-10.7, AC-10.9
// to AC-10.11, AC-10.13, AC-10.15, AC-10.16. Only a validated amount moves the reservation, and a decision is final.
class ReservationProofReviewTest {

    private static final UUID ID = new UUID(10, 1), TENANT = new UUID(10, 2), CUSTOMER = new UUID(10, 3), DEPARTURE = new UUID(10, 4),
            SERVICE = new UUID(10, 5), CREATOR = new UUID(10, 6), OPERATOR = new UUID(10, 7), REVIEWER = new UUID(10, 8),
            FIRST = new UUID(10, 0xa1), SECOND = new UUID(10, 0xa2), CASH = new UUID(10, 0xa3);
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final Instant LATER = NOW.plus(Duration.ofHours(2));
    private static final Cents MIN = new Cents(100_000);

    // Two people, 300.000, no discount.
    private static Reservation born(PaymentType type) {
        return Reservation.create(ID, TENANT, CUSTOMER, DEPARTURE, CREATOR, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                type, PaymentMethod.TRANSFER, NOW);
    }

    private static Cents minimumFor(PaymentType type) {
        return type == PaymentType.DEPOSIT ? MIN : null;
    }

    private static Reservation withTransfer(PaymentType type, long declared) {
        Reservation reservation = born(type);
        reservation.registerTransfer(FIRST, new Cents(declared), minimumFor(type), OPERATOR, NOW);
        reservation.allocateSeat(NOW);
        reservation.pullEvents();
        return reservation;
    }

    private static ProofDecision approve(Reservation reservation, UUID attempt, long verified, String reason) {
        return reservation.approve(attempt, new Cents(verified), reason, minimumFor(reservation.expectedPaymentType()), REVIEWER, LATER);
    }

    private static PaymentAttempt attempt(Reservation reservation, UUID id) {
        return reservation.attempts().stream().filter(found -> found.attemptId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void rejectionKeepsActorDateTimeAndReasonAndChangesNothingElse() {
        Reservation reservation = withTransfer(PaymentType.DEPOSIT, 100_000);

        PaymentAttempt rejected = reservation.reject(FIRST, "the amount does not match", REVIEWER, LATER);

        assertThat(rejected.validationStatus()).isEqualTo(ValidationStatus.REJECTED);
        assertThat(rejected.decidedBy()).isEqualTo(REVIEWER);
        assertThat(rejected.decidedAt()).isEqualTo(LATER);
        assertThat(rejected.decisionReason()).isEqualTo("the amount does not match");
        assertThat(rejected.verifiedAmount()).isNull();
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.NO_PAYMENT);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(300_000));
        assertThat(reservation.pullEvents()).singleElement().isInstanceOfSatisfying(PaymentRejected.class, e -> {
            assertThat(e.reason()).isEqualTo("the amount does not match");
            assertThat(e.pendingBalance()).isEqualTo(new Cents(300_000));
            assertThat(e.validatorId()).isEqualTo(REVIEWER);
        });
    }

    @Test
    void emptyReasonOrNonPositiveVerifiedAmountIsRefusedNamingTheField() {
        Reservation reservation = withTransfer(PaymentType.DEPOSIT, 100_000);

        assertThatThrownBy(() -> reservation.reject(FIRST, "  ", REVIEWER, LATER)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("reason"));
        assertThatThrownBy(() -> approve(reservation, FIRST, 0, "x")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("verifiedAmountCents"));
        assertThatThrownBy(() -> reservation.approve(FIRST, null, null, MIN, REVIEWER, LATER)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("verifiedAmountCents"));
        assertThat(attempt(reservation, FIRST).validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
    }

    @Test
    void reasonIsOptionalOnlyWhenTheVerifiedAmountEqualsTheDeclaredOne() {
        Reservation reservation = withTransfer(PaymentType.DEPOSIT, 100_000);

        assertThatThrownBy(() -> approve(reservation, FIRST, 120_000, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("reason"));
        assertThat(approve(reservation, FIRST, 100_000, null).attempt().validationStatus()).isEqualTo(ValidationStatus.APPROVED);
    }

    @Test
    void aDecidedProofCannotBeDecidedAgain() {
        Reservation approved = withTransfer(PaymentType.DEPOSIT, 100_000);
        approve(approved, FIRST, 100_000, null);
        Reservation rejected = withTransfer(PaymentType.DEPOSIT, 100_000);
        rejected.reject(FIRST, "illegible", REVIEWER, LATER);

        assertThatThrownBy(() -> approve(approved, FIRST, 100_000, null)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> approved.reject(FIRST, "late", REVIEWER, LATER)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> approve(rejected, FIRST, 100_000, null)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(attempt(rejected, FIRST).decisionReason()).isEqualTo("illegible");
    }

    @Test
    void aProofUnderValidationNeverConfirmsAndAnUnknownOrCashAttemptCannotBeDecided() {
        Reservation reservation = withTransfer(PaymentType.DEPOSIT, 100_000);
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.pendingBalance()).isEqualTo(reservation.finalValue());

        assertThatThrownBy(() -> approve(reservation, SECOND, 100_000, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("attemptId"));
        Reservation withCash = born(PaymentType.FULL_PAYMENT);
        withCash.registerCash(CASH, new Cents(10_000), null, OPERATOR, NOW);
        assertThatThrownBy(() -> approve(withCash, CASH, 10_000, null)).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void minimumDepositApprovedConfirmsWithDepositReceived() {
        Reservation reservation = withTransfer(PaymentType.DEPOSIT, 100_000);

        ProofDecision decision = approve(reservation, FIRST, 100_000, null);

        assertThat(decision.applied()).isEqualTo(new Cents(100_000));
        assertThat(decision.excess()).isEqualTo(new Cents(0));
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(200_000));
        assertThat(attempt(reservation, FIRST).decidedBy()).isEqualTo(REVIEWER);
        assertThat(reservation.pullEvents()).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("PaymentValidated", "ReservationConfirmed");
    }

    @Test
    void fullPaymentConfirmsOnlyAtOneHundredPercent() {
        Reservation reservation = withTransfer(PaymentType.FULL_PAYMENT, 100_000);
        approve(reservation, FIRST, 100_000, null);

        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.PARTIAL);

        reservation.registerTransfer(SECOND, new Cents(200_000), null, OPERATOR, NOW);
        approve(reservation, SECOND, 200_000, null);

        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(0));
    }

    @Test
    void belowTheMinimumTheSituationIsPartialAndTheReservationStaysPending() {
        Reservation reservation = withTransfer(PaymentType.DEPOSIT, 100_000);

        approve(reservation, FIRST, 60_000, "only 60.000 arrived");

        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(240_000));
        assertThat(attempt(reservation, FIRST).verifiedAmount()).isEqualTo(new Cents(60_000));
        assertThat(attempt(reservation, FIRST).decisionReason()).isEqualTo("only 60.000 arrived");
    }

    @Test
    void rejectionLeavesValidatedPaymentsUntouchedAndTheRejectedAttemptStaysInTheHistory() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);
        reservation.registerCash(CASH, new Cents(100_000), null, OPERATOR, NOW);
        reservation.registerTransfer(FIRST, new Cents(100_000), null, OPERATOR, NOW);

        reservation.reject(FIRST, "illegible", REVIEWER, LATER);
        reservation.registerTransfer(SECOND, new Cents(100_000), null, OPERATOR, LATER);

        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(200_000));
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        assertThat(reservation.attempts()).extracting(PaymentAttempt::attemptId).containsExactly(CASH, FIRST, SECOND);
        assertThat(attempt(reservation, FIRST).validationStatus()).isEqualTo(ValidationStatus.REJECTED);
        assertThat(attempt(reservation, SECOND).validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
    }

    @Test
    void approvalAppliesOnlyThePendingBalanceAndRecordsTheExcess() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);
        reservation.registerTransfer(FIRST, new Cents(200_000), null, OPERATOR, NOW);
        reservation.registerCash(CASH, new Cents(150_000), null, OPERATOR, NOW);
        reservation.pullEvents();

        ProofDecision decision = approve(reservation, FIRST, 200_000, null);

        assertThat(decision.applied()).isEqualTo(new Cents(150_000));
        assertThat(decision.excess()).isEqualTo(new Cents(50_000));
        assertThat(attempt(reservation, FIRST).appliedAmount()).isEqualTo(new Cents(150_000));
        assertThat(attempt(reservation, FIRST).excessAmount()).isEqualTo(new Cents(50_000));
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(0));
    }

    @Test
    void approvingOnACancelledReservationNeverAddsToValidatedValuesAndTheWholeAmountIsForTreatment() {
        Reservation reservation = withTransfer(PaymentType.DEPOSIT, 100_000);
        List<PaymentAttempt> attempts = reservation.attempts();
        reservation.restoredAs(ReservationStatus.CANCELLED, PaymentStatus.NO_PAYMENT, new Cents(300_000), false, attempts);

        ProofDecision decision = approve(reservation, FIRST, 100_000, null);

        assertThat(decision.applied()).isEqualTo(new Cents(0));
        assertThat(decision.excess()).isEqualTo(new Cents(0));
        assertThat(decision.treatment()).isEqualTo(new Cents(100_000));
        assertThat(attempt(reservation, FIRST).treatmentAmount()).isEqualTo(new Cents(100_000));
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(300_000));
        assertThat(reservation.pullEvents()).extracting(e -> e.getClass().getSimpleName()).containsExactly("PaymentValidated");
    }

    @Test
    void theFirstPaymentRejectionReleasesTheSeatOnlyWhenNothingElseIsUnderReviewOrValidated() {
        Reservation alone = withTransfer(PaymentType.DEPOSIT, 100_000);
        alone.reject(FIRST, "illegible", REVIEWER, LATER);
        assertThat(alone.shouldReleaseSeatAfterRejection()).isTrue();
        alone.pullEvents();

        alone.releaseSeat("FIRST_PAYMENT_REJECTED", LATER);

        assertThat(alone.seatHeld()).isFalse();
        assertThat(alone.pullEvents()).singleElement().isInstanceOfSatisfying(CapacityReleased.class, e -> {
            assertThat(e.salidaId()).isEqualTo(DEPARTURE);
            assertThat(e.reason()).isEqualTo("FIRST_PAYMENT_REJECTED");
        });

        Reservation other = withTransfer(PaymentType.FULL_PAYMENT, 100_000);
        other.registerTransfer(SECOND, new Cents(50_000), null, OPERATOR, NOW);
        other.reject(FIRST, "illegible", REVIEWER, LATER);
        assertThat(other.shouldReleaseSeatAfterRejection()).isFalse();

        Reservation confirmed = withTransfer(PaymentType.DEPOSIT, 100_000);
        approve(confirmed, FIRST, 100_000, null);
        assertThat(confirmed.shouldReleaseSeatAfterRejection()).isFalse();
    }

    @Test
    void theReviewMarkAppearsAtTwentyFourHoursWithoutChangingTheState() {
        PaymentAttempt attempt = withTransfer(PaymentType.DEPOSIT, 100_000).attempts().get(0);

        assertThat(attempt.reviewDueAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(attempt.reviewOverdue(NOW.plus(Duration.ofHours(23)).plusSeconds(3_540))).isFalse();
        assertThat(attempt.reviewOverdue(NOW.plus(Duration.ofHours(24)))).isTrue();
        assertThat(attempt.validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
    }

    @Test
    void aDecidedProofAndACashPaymentHaveNoReviewMark() {
        Reservation reservation = withTransfer(PaymentType.FULL_PAYMENT, 100_000);
        reservation.registerCash(CASH, new Cents(10_000), null, OPERATOR, NOW);
        reservation.reject(FIRST, "illegible", REVIEWER, LATER);
        Instant farLater = NOW.plus(Duration.ofDays(5));

        assertThat(attempt(reservation, FIRST).reviewOverdue(farLater)).isFalse();
        assertThat(attempt(reservation, CASH).reviewOverdue(farLater)).isFalse();
    }
}
