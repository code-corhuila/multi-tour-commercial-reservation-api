package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// AC-10.10: a decided proof is final and cash is never a proof to review. The HTTP adapter answers 422 INVALID_STATUS_TRANSITION,
// never 409 (guidelines.md).
public final class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
