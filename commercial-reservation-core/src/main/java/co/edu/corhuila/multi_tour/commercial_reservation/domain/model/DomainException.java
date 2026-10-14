package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

public class DomainException extends RuntimeException {

    private final String field;

    public DomainException(String field, String message) {
        super(message);
        this.field = field;
    }

    public DomainException(String message) {
        this(null, message);
    }

    // Lets the HTTP adapter name the offending field without parsing the message (AC-01.2).
    public String field() {
        return field;
    }
}
