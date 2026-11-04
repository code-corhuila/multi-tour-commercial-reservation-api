package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03 and T-05 (domain part) of HU-COM-011: RN-04, RN-06, RN-08; AC-11.1, AC-11.2, AC-11.4, AC-11.11, AC-11.13. A reservation is
// expirable only when its deadline was reached, nothing paid and no proof presented in time is under validation.
class ReservationExpiryTest {

    private static final UUID ID = new UUID(11, 1), TENANT = new UUID(11, 2), CUSTOMER = new UUID(11, 3), DEPARTURE = new UUID(11, 4),
            SERVICE = new UUID(11, 5), OPERATOR = new UUID(11, 7), FIRST = new UUID(11, 0xa1), CASH = new UUID(11, 0xa3);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant DEADLINE = CREATED.plus(Duration.ofHours(24));
    private static final Cents MIN = new Cents(100_000);

    private static Reservation born(PaymentType type) {
        return Reservation.create(ID, TENANT, CUSTOMER, DEPARTURE, OPERATOR, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                type, PaymentMethod.TRANSFER, CREATED);
    }

    private static ExpiryVerdict initial(Reservation reservation, Instant now) {
        return reservation.expiryVerdict(DeadlineKind.INITIAL_PAYMENT, DEADLINE, false, now);
    }

    @Test
    void aProofInValidationPresentedInTimeBlocksTheExpiry() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);
        reservation.registerTransfer(FIRST, new Cents(300_000), null, OPERATOR, DEADLINE.minus(Duration.ofHours(1)));

        assertThat(initial(reservation, DEADLINE.plus(Duration.ofHours(5)))).isEqualTo(ExpiryVerdict.PROOF_IN_VALIDATION);
    }

    @Test
    void aProofPresentedAfterTheDeadlineDoesNotBlockTheExpiry() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);
        reservation.registerTransfer(FIRST, new Cents(300_000), null, OPERATOR, DEADLINE.plusSeconds(1));

        assertThat(initial(reservation, DEADLINE.plus(Duration.ofHours(5)))).isEqualTo(ExpiryVerdict.EXPIRABLE);
    }

    @Test
    void aValidatedPaymentOrCashReceivedBlocksTheExpiry() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);
        reservation.registerCash(CASH, new Cents(300_000), null, OPERATOR, DEADLINE.minus(Duration.ofHours(2)));

        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(initial(reservation, DEADLINE.plus(Duration.ofDays(1)))).isEqualTo(ExpiryVerdict.PAID);
    }

    @Test
    void evidenceAtTheExactDeadlinePrevailsOverTheCancellation() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);
        reservation.registerTransfer(FIRST, new Cents(300_000), null, OPERATOR, DEADLINE);

        assertThat(initial(reservation, DEADLINE)).isEqualTo(ExpiryVerdict.PROOF_IN_VALIDATION);
        assertThat(initial(born(PaymentType.FULL_PAYMENT), DEADLINE.minusSeconds(1))).isEqualTo(ExpiryVerdict.NOT_DUE);
        assertThat(initial(born(PaymentType.FULL_PAYMENT), DEADLINE)).isEqualTo(ExpiryVerdict.EXPIRABLE);
    }

    @Test
    void aFrozenOrCancelledReservationIsNeverExpirable() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);
        assertThat(reservation.expiryVerdict(DeadlineKind.INITIAL_PAYMENT, DEADLINE, true, DEADLINE.plusSeconds(9))).isEqualTo(ExpiryVerdict.FROZEN);

        reservation.cancel(CancellationCause.PAYMENT_EXPIRY, null, DEADLINE, 0);
        assertThat(initial(reservation, DEADLINE.plusSeconds(9))).isEqualTo(ExpiryVerdict.ALREADY_CANCELLED);
    }

    @Test
    void aBalanceDeadlineExpiresOnlyWhileTheBalanceIsPending() {
        Reservation deposit = born(PaymentType.DEPOSIT);
        deposit.registerCash(CASH, MIN, MIN, OPERATOR, CREATED);

        assertThat(deposit.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(deposit.expiryVerdict(DeadlineKind.BALANCE, DEADLINE, false, DEADLINE)).isEqualTo(ExpiryVerdict.EXPIRABLE);

        Reservation settled = born(PaymentType.FULL_PAYMENT);
        settled.registerCash(CASH, new Cents(300_000), null, OPERATOR, CREATED);
        assertThat(settled.expiryVerdict(DeadlineKind.BALANCE, DEADLINE, false, DEADLINE)).isEqualTo(ExpiryVerdict.PAID);
    }

    @Test
    void cancellingRecordsTheCauseTheInstantAndTheEventOnceAndNeverTwice() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);
        reservation.cancel(CancellationCause.PAYMENT_EXPIRY, null, DEADLINE, 2);

        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.cancellationCause()).isEqualTo(CancellationCause.PAYMENT_EXPIRY);
        assertThat(reservation.cancelledAt()).isEqualTo(DEADLINE);
        assertThat(reservation.cancelledBy()).isNull();
        assertThat(reservation.pullEvents()).containsExactly(new ReservationCancelled(TENANT, ID, CancellationCause.PAYMENT_EXPIRY, 2, DEADLINE));
        assertThatThrownBy(() -> reservation.cancel(CancellationCause.PAYMENT_EXPIRY, null, DEADLINE, 0))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
