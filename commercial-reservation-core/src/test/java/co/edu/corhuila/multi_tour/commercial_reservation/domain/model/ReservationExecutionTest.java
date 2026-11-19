package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// G-API4-1 (RN-DEX-01; PDR RN-EJE-001, CA-029): only a confirmed and fully paid reservation starts and only one in execution completes; any other keeps its state.
class ReservationExecutionTest {
    private static final UUID ID = new UUID(25, 1), TENANT = new UUID(25, 2), CUSTOMER = new UUID(25, 3), DEPARTURE = new UUID(25, 4), SERVICE = new UUID(25, 5),
            OPERATOR = new UUID(25, 7), ATTEMPT = new UUID(25, 0xa1);
    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final Cents MINIMUM = new Cents(100_000);

    private static Reservation born(PaymentType type) {
        return Reservation.create(ID, TENANT, CUSTOMER, DEPARTURE, OPERATOR, 2, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(300_000), new Cents(300_000), List.of(), type, PaymentMethod.TRANSFER, NOW);
    }

    private static Reservation paidInFull() {
        Reservation reservation = born(PaymentType.FULL_PAYMENT);
        reservation.registerCash(ATTEMPT, new Cents(300_000), null, OPERATOR, NOW);
        return reservation;
    }

    @Test void onlyAConfirmedFullyPaidReservationStarts() {
        Reservation reservation = paidInFull();
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);

        assertThat(reservation.startExecution()).isTrue();

        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.IN_EXECUTION);
    }

    @Test void aReservationNotFullyPaidOrNotConfirmedOrCancelledKeepsItsState() {
        Reservation pending = born(PaymentType.FULL_PAYMENT);
        assertThat(pending.startExecution()).isFalse();
        assertThat(pending.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);

        Reservation deposit = born(PaymentType.DEPOSIT);
        deposit.registerCash(ATTEMPT, MINIMUM, MINIMUM, OPERATOR, NOW);
        assertThat(deposit.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(deposit.paymentStatus()).isNotEqualTo(PaymentStatus.FULLY_PAID);
        assertThat(deposit.startExecution()).isFalse();
        assertThat(deposit.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        Reservation cancelled = born(PaymentType.FULL_PAYMENT);
        cancelled.cancel(CancellationCause.CUSTOMER_REQUEST, OPERATOR, NOW, 0);
        assertThat(cancelled.startExecution()).isFalse();
        assertThat(cancelled.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test void onlyAReservationInExecutionCompletes() {
        Reservation reservation = paidInFull();
        assertThat(reservation.completeExecution()).isFalse();
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        reservation.startExecution();
        assertThat(reservation.completeExecution()).isTrue();
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(reservation.completeExecution()).isFalse();
        assertThat(reservation.startExecution()).isFalse();
    }
}
