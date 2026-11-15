package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// A percentage as an integer in basis points (1 % = 100, 100 % = 10_000). No double crosses a port (plan D-08).
public record BasisPoints(int value) {

    public static final int FULL = 10_000;

    public BasisPoints {
        if (value < 0 || value > FULL) {
            throw new DomainException("rate", "a rate must be between 0 and " + FULL + " basis points");
        }
    }

    // The field name travels in the exception so the caller can name it to the user (AC-01.2).
    public static BasisPoints of(String field, Long value) {
        if (value == null) {
            throw new DomainException(field, field + " is required");
        }
        if (value < 0 || value > FULL) {
            throw new DomainException(field, field + " must be between 0 and " + FULL + " basis points");
        }
        return new BasisPoints(value.intValue());
    }
}
