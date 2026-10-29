package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// One line of the breakdown: the base it applies to, the amount taken off and the resulting subtotal (RN-03).
// Integers only; the subtotal never goes below zero.
public record DiscountLine(Cents base, Cents amount, Cents subtotal) {

    public DiscountLine {
        if (base == null || amount == null || subtotal == null) {
            throw new DomainException("discountLine", "a discount line needs base, amount and subtotal");
        }
        if (amount.amount() > base.amount() || subtotal.amount() != base.amount() - amount.amount()) {
            throw new DomainException("discountLine", "subtotal must be the base minus the amount, never below zero");
        }
    }

    // TODO DP-02: rounding is half up per line, PROPOSED only; no source confirms it yet.
    public static DiscountLine percentage(Cents base, BasisPoints rate) {
        long scaled;
        try {
            scaled = Math.addExact(Math.multiplyExact(base.amount(), (long) rate.value()), BasisPoints.FULL / 2);
        } catch (ArithmeticException overflow) {
            throw new DomainException("base", "base is too large to apply a percentage");
        }
        return taking(base, new Cents(scaled / BasisPoints.FULL));
    }

    // An amount above the base takes the whole base, so the subtotal stays at zero.
    public static DiscountLine fixedAmount(Cents base, Cents amount) {
        return taking(base, new Cents(Math.min(base.amount(), amount.amount())));
    }

    private static DiscountLine taking(Cents base, Cents amount) {
        return new DiscountLine(base, amount, new Cents(base.amount() - amount.amount()));
    }
}
