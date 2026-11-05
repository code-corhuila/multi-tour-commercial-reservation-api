package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03 of HU-COM-012 (aggregate side): RN-04; AC-12.3, AC-12.14. The PDR example: a value of $300.000 raised by $100.000.
class ReservationModifySituationTest {

    private static final UUID ID = new UUID(12, 31), TENANT = new UUID(12, 32), CUSTOMER = new UUID(12, 33), DEPARTURE = new UUID(12, 34),
            SERVICE = new UUID(12, 35), CREATOR = new UUID(12, 36);
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-11-20T08:00:00Z");
    private static final ReservedService TOUR = new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000));

    private static Reservation born(PaymentType type) {
        return Reservation.create(ID, TENANT, CUSTOMER, DEPARTURE, CREATOR, 2, TOUR, new Cents(300_000), new Cents(300_000), List.of(), type,
                PaymentMethod.TRANSFER, NOW);
    }

    private static Reservation confirmed(PaymentType type, long paid) {
        Reservation reservation = born(type);
        reservation.registerCash(new UUID(12, 40), new Cents(paid), new Cents(100_000), CREATOR, NOW);
        return reservation;
    }

    @Test
    void increaseOnAConfirmedReservationKeepsItConfirmedWithADifferencePending() {
        Reservation reservation = confirmed(PaymentType.FULL_PAYMENT, 300_000);

        ModificationOutcome outcome = reservation.modify(3, TOUR, DEPARTURE, List.of(), START, NOW);
        reservation.settleSituation(null);

        assertThat(outcome.newFinalValue()).isEqualTo(new Cents(450_000));
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.pendingBalance()).isEqualTo(new Cents(150_000));
    }

    @Test
    void theSituationIsBalancePendingForFullPaymentAndDepositReceivedForDeposit() {
        Reservation full = confirmed(PaymentType.FULL_PAYMENT, 300_000);
        full.modify(3, TOUR, DEPARTURE, List.of(), START, NOW);
        full.settleSituation(null);
        assertThat(full.paymentStatus()).isEqualTo(PaymentStatus.BALANCE_PENDING);

        Reservation deposit = confirmed(PaymentType.DEPOSIT, 100_000);
        deposit.modify(3, TOUR, DEPARTURE, List.of(), START, NOW);
        deposit.settleSituation(new Cents(150_000));
        assertThat(deposit.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
    }

    @Test
    void theIncreaseDoesNotRequireTheMinimumAgain() {
        Reservation deposit = confirmed(PaymentType.DEPOSIT, 100_000);

        deposit.modify(4, TOUR, DEPARTURE, List.of(), START, NOW);
        deposit.settleSituation(new Cents(200_000));

        assertThat(deposit.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(deposit.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
    }

    @Test
    void aModificationNeverConfirmsAndTheThresholdFollowsTheNewValue() {
        Reservation pending = born(PaymentType.DEPOSIT);

        pending.modify(3, TOUR, DEPARTURE, List.of(), START, NOW);
        pending.settleSituation(new Cents(1));

        assertThat(pending.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(pending.paymentStatus()).isEqualTo(PaymentStatus.NO_PAYMENT);
    }
}
