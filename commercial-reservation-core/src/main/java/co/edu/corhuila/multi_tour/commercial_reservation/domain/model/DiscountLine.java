package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// STUB (red step)
public record DiscountLine(Cents base, Cents amount, Cents subtotal) {
    public static DiscountLine percentage(Cents base, BasisPoints rate) {
        return new DiscountLine(base, new Cents(0), base);
    }

    public static DiscountLine fixedAmount(Cents base, Cents amount) {
        return new DiscountLine(base, new Cents(0), base);
    }
}
