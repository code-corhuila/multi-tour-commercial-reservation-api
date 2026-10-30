package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// T-01 of HU-COM-009: RN-02. The situation is a pure function of validated values (PDR RN-RES-006A, models.md).
class FinancialSituationTest {

    private static final Cents FINAL = new Cents(300_000), MIN = new Cents(100_000);

    @Test
    void financialSituationIsDerivedFromValidatedValuesOnly() {
        assertThat(FinancialSituation.of(FINAL, new Cents(0), PaymentType.DEPOSIT, MIN, ReservationStatus.PENDING_PAYMENT))
                .isEqualTo(PaymentStatus.NO_PAYMENT);
        assertThat(FinancialSituation.of(FINAL, new Cents(0), PaymentType.FULL_PAYMENT, null, ReservationStatus.PENDING_PAYMENT))
                .isEqualTo(PaymentStatus.NO_PAYMENT);
    }

    @Test
    void belowTheMinimumTheSituationIsPartial() {
        assertThat(FinancialSituation.of(FINAL, new Cents(99_999), PaymentType.DEPOSIT, MIN, ReservationStatus.PENDING_PAYMENT))
                .isEqualTo(PaymentStatus.PARTIAL);
    }

    @Test
    void theMinimumDepositGivesDepositReceived() {
        assertThat(FinancialSituation.of(FINAL, new Cents(100_000), PaymentType.DEPOSIT, MIN, ReservationStatus.PENDING_PAYMENT))
                .isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
        assertThat(FinancialSituation.of(FINAL, new Cents(299_999), PaymentType.DEPOSIT, MIN, ReservationStatus.CONFIRMED))
                .isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
    }

    @Test
    void aFullPaymentBelowTheTotalIsPartialUntilConfirmedThenBalancePending() {
        assertThat(FinancialSituation.of(FINAL, new Cents(100_000), PaymentType.FULL_PAYMENT, null, ReservationStatus.PENDING_PAYMENT))
                .isEqualTo(PaymentStatus.PARTIAL);
        assertThat(FinancialSituation.of(FINAL, new Cents(250_000), PaymentType.FULL_PAYMENT, null, ReservationStatus.CONFIRMED))
                .isEqualTo(PaymentStatus.BALANCE_PENDING);
    }

    @Test
    void theWholeValueGivesFullyPaidForBothTypes() {
        assertThat(FinancialSituation.of(FINAL, FINAL, PaymentType.DEPOSIT, MIN, ReservationStatus.PENDING_PAYMENT))
                .isEqualTo(PaymentStatus.FULLY_PAID);
        assertThat(FinancialSituation.of(FINAL, FINAL, PaymentType.FULL_PAYMENT, null, ReservationStatus.PENDING_PAYMENT))
                .isEqualTo(PaymentStatus.FULLY_PAID);
    }

    @Test
    void theConditionToConfirmIsTheTotalForFullPaymentAndTheMinimumForADeposit() {
        assertThat(FinancialSituation.meetsConfirmation(FINAL, new Cents(299_999), PaymentType.FULL_PAYMENT, null)).isFalse();
        assertThat(FinancialSituation.meetsConfirmation(FINAL, FINAL, PaymentType.FULL_PAYMENT, null)).isTrue();
        assertThat(FinancialSituation.meetsConfirmation(FINAL, new Cents(99_999), PaymentType.DEPOSIT, MIN)).isFalse();
        assertThat(FinancialSituation.meetsConfirmation(FINAL, new Cents(100_000), PaymentType.DEPOSIT, MIN)).isTrue();
    }

    @Test
    void aDepositWithoutItsMinimumIsRefused() {
        assertThatThrownBy(() -> FinancialSituation.of(FINAL, new Cents(1), PaymentType.DEPOSIT, null, ReservationStatus.PENDING_PAYMENT))
                .isInstanceOf(DomainException.class);
    }
}
