package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// Spec 026 (-api), AC-26.4 and AC-26.5: the share of the affected travelers and the penalty, with exact integers and rounding in the customer's favour.
class RefundCalculationTest {

    @Test
    void computesTheShareOfTheAffectedTravelersAndRoundsThePenaltyDown() {
        RefundCalculation result = RefundCalculation.of(new Cents(100_001), 3, 1, 2_500);

        assertThat(result.share()).isEqualTo(new Cents(33_333));
        assertThat(result.penalty()).isEqualTo(new Cents(8_333));
        assertThat(result.refund()).isEqualTo(new Cents(25_000));
    }

    @Test
    void everyTravelerAffectedWithNoPenaltyRefundsTheWholeValue() {
        RefundCalculation result = RefundCalculation.of(new Cents(90_000), 4, 4, 0);

        assertThat(result.share()).isEqualTo(new Cents(90_000));
        assertThat(result.penalty()).isEqualTo(new Cents(0));
        assertThat(result.refund()).isEqualTo(new Cents(90_000));
    }

    @Test
    void aFullPenaltyLeavesNothingToRefund() {
        assertThat(RefundCalculation.of(new Cents(90_000), 2, 1, 10_000).refund()).isEqualTo(new Cents(0));
    }

    @Test
    void aSingleCentIsNeverRefundedToAShareOfZero() {
        RefundCalculation result = RefundCalculation.of(new Cents(1), 2, 1, 0);

        assertThat(result.share()).isEqualTo(new Cents(0));
        assertThat(result.refund()).isEqualTo(new Cents(0));
    }

    @Test
    void aVeryLargeValueDoesNotOverflow() {
        RefundCalculation result = RefundCalculation.of(new Cents(Long.MAX_VALUE / 2), 7, 7, 10_000);

        assertThat(result.share()).isEqualTo(new Cents(Long.MAX_VALUE / 2));
        assertThat(result.refund()).isEqualTo(new Cents(0));
    }

    @Test
    void refusesTravelersOutOfRangeAndPointsOutOfRange() {
        assertThatThrownBy(() -> RefundCalculation.of(new Cents(10), 2, 0, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> RefundCalculation.of(new Cents(10), 2, 3, 0)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> RefundCalculation.of(new Cents(10), 2, 1, 10_001)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> RefundCalculation.of(new Cents(10), 2, 1, -1)).isInstanceOf(DomainException.class);
    }
}
