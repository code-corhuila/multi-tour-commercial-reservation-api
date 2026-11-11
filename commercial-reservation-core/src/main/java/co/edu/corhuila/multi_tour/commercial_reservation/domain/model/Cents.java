package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// Minor units as a long: no decimal type may cross a port (plan D-08).
public record Cents(long amount) {

    public Cents {
        if (amount < 0) {
            throw new DomainException("amount", "an amount cannot be negative");
        }
    }

    // The field name travels in the exception so the caller can name it to the user (AC-01.2).
    public static Cents of(String field, Long amount) {
        if (amount == null) {
            throw new DomainException(field, field + " is required");
        }
        if (amount < 0) {
            throw new DomainException(field, field + " cannot be negative");
        }
        return new Cents(amount);
    }
}
