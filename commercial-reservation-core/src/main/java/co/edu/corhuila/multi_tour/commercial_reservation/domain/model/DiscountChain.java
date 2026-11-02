package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.List;

// The lines of a reservation form a chain (RN-RES-002): the first one starts from the projected value and each next one
// from the subtotal the line before left. A line never takes more than the previous subtotal, and its subtotal is the
// previous subtotal minus its amount, whatever base it was calculated on. A base on the original value is that projected
// value; a base on the previous subtotal is the subtotal left by the line before.
final class DiscountChain {

    private DiscountChain() {
    }

    static Cents lastSubtotal(Cents original, List<FixedDiscount> lines) {
        long previous = original.amount();
        for (FixedDiscount line : lines) {
            long expectedBase = line.calculationBase() == DiscountBase.ORIGINAL_VALUE ? original.amount() : previous;
            if (line.base().amount() != expectedBase) {
                throw new DomainException("discounts", "the base of a discount line must be the one its calculation base names");
            }
            if (line.amount().amount() > previous || line.subtotal().amount() != previous - line.amount().amount()) {
                throw new DomainException("discounts", "the subtotal of a discount line must be the previous subtotal minus its amount, never below zero");
            }
            previous = line.subtotal().amount();
        }
        return new Cents(previous);
    }
}
