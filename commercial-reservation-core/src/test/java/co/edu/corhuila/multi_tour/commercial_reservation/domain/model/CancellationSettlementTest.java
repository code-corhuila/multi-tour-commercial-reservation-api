package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

// HU-COM-013 T-02: RN-04; AC-13.3. Refundable = validated - penalty; the penalty is the policy percentage of the validated values, floored
// to the cent (plan D-03), never above them. With no policy the penalty is zero.
class CancellationSettlementTest {

    @Test
    void refundableIsValidatedMinusPenaltyPdrExample() {
        CancellationSettlement settlement = CancellationSettlement.of(new Cents(30_000_000), OptionalInt.of(5_000));

        assertThat(settlement.penalty()).isEqualTo(new Cents(15_000_000));
        assertThat(settlement.refundable()).isEqualTo(new Cents(15_000_000));
        assertThat(settlement.penaltyBasisPoints()).isEqualTo(5_000);
        assertThat(settlement.validated()).isEqualTo(new Cents(30_000_000));
    }

    @Test
    void withNoPolicyTheRefundableIsTheWholeValidatedAmount() {
        CancellationSettlement settlement = CancellationSettlement.of(new Cents(30_000_000), OptionalInt.empty());

        assertThat(settlement.penaltyBasisPoints()).isZero();
        assertThat(settlement.penalty()).isEqualTo(new Cents(0));
        assertThat(settlement.refundable()).isEqualTo(new Cents(30_000_000));
    }

    @Test
    void aPenaltyNeverExceedsTheValidatedValues() {
        CancellationSettlement all = CancellationSettlement.of(new Cents(123_457), OptionalInt.of(10_000));

        assertThat(all.penalty()).isEqualTo(new Cents(123_457));
        assertThat(all.refundable()).isEqualTo(new Cents(0));
        assertThatThrownBy(() -> CancellationSettlement.of(new Cents(100), OptionalInt.of(10_001))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> CancellationSettlement.of(new Cents(100), OptionalInt.of(-1))).isInstanceOf(DomainException.class);
    }

    @Test
    void thePenaltyIsRoundedDownToTheCent() {
        CancellationSettlement settlement = CancellationSettlement.of(new Cents(100_001), OptionalInt.of(3_333));

        assertThat(settlement.penalty()).isEqualTo(new Cents(33_330));
        assertThat(settlement.refundable()).isEqualTo(new Cents(66_671));
    }
}
