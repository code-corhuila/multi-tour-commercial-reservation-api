package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

// RN-03 / AC-04.5. Integers only. Rounding half up per line is PROPOSED (DP-02), not confirmed.
class DiscountLineTest {

    private static Cents c(long v) {
        return new Cents(v);
    }

    @Test
    void aPercentageInBasisPointsGivesExactCents() {
        DiscountLine line = DiscountLine.percentage(c(100_000), BasisPoints.of("rate", 1_000L));
        assertEquals(c(100_000), line.base());
        assertEquals(c(10_000), line.amount());
        assertEquals(c(90_000), line.subtotal());
    }

    // DP-02 PROPOSED: half up per line.
    @Test
    void aPercentageRoundsHalfUpPerLineProposedDp02() {
        // 1 cent * 50 % = 0.5 -> 1 ; 3 cents * 50 % = 1.5 -> 2 ; 1 cent * 49.99 % = 0.4999 -> 0
        assertEquals(c(1), DiscountLine.percentage(c(1), BasisPoints.of("rate", 5_000L)).amount());
        assertEquals(c(2), DiscountLine.percentage(c(3), BasisPoints.of("rate", 5_000L)).amount());
        assertEquals(c(0), DiscountLine.percentage(c(1), BasisPoints.of("rate", 4_999L)).amount());
    }

    @Test
    void aFixedAmountIsSubtractedInWholeCents() {
        DiscountLine line = DiscountLine.fixedAmount(c(90_000), c(25_050));
        assertEquals(c(90_000), line.base());
        assertEquals(c(25_050), line.amount());
        assertEquals(c(64_950), line.subtotal());
    }

    @Test
    void aFixedAmountAboveTheBaseNeverTakesTheSubtotalBelowZero() {
        DiscountLine line = DiscountLine.fixedAmount(c(10_000), c(50_000));
        assertEquals(c(10_000), line.amount());
        assertEquals(c(0), line.subtotal());
    }

    @Test
    void oneHundredPercentLeavesZero() {
        DiscountLine line = DiscountLine.percentage(c(12_345), BasisPoints.of("rate", 10_000L));
        assertEquals(c(12_345), line.amount());
        assertEquals(c(0), line.subtotal());
    }

    @Test
    void theSameInputsGiveTheSameLineTwice() {
        assertEquals(
                DiscountLine.percentage(c(33_333), BasisPoints.of("rate", 1_234L)),
                DiscountLine.percentage(c(33_333), BasisPoints.of("rate", 1_234L)));
    }

    @Test
    void basisPointsOutsideZeroToTenThousandAreRefusedNamingTheField() {
        DomainException above = assertThrows(DomainException.class, () -> BasisPoints.of("rate", 10_001L));
        assertEquals("rate", above.field());
        assertThrows(DomainException.class, () -> BasisPoints.of("rate", -1L));
        assertThrows(DomainException.class, () -> BasisPoints.of("rate", null));
    }

    @Test
    void anEnormousBaseIsRefusedInsteadOfOverflowing() {
        assertThrows(DomainException.class,
                () -> DiscountLine.percentage(c(Long.MAX_VALUE), BasisPoints.of("rate", 5_000L)));
    }
}
