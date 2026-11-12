package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// The cash did not answer, answered with an error or is not configured: the income is unknown, so the payment is not registered.
// The request can be repeated with the same Idempotency-Key without recording the income twice.
public final class CashUnavailableException extends RuntimeException {
    public CashUnavailableException(String message) {
        super(message);
    }

    public CashUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
