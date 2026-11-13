package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-010 DP-11: a transfer is approved only by the saga register-payment (Cash records the income first, step 2 applies the amount).
// The human route of approval is closed; the HTTP adapter answers this as a business rule and nothing is recorded.
public final class ApprovalThroughSagaRequiredException extends DomainException {
    public ApprovalThroughSagaRequiredException() {
        super("a payment proof is approved through the saga register-payment, which records the income in the cash first; this route does not apply the amount");
    }
}
