package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

public final class BusinessRuleViolation extends RuntimeException {
    public BusinessRuleViolation(String message, Throwable cause) {
        super(message, cause);
    }
}
