package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-016 (spec 026, AC-26.3, AC-26.7): nothing is paid or the policy keeps everything, so no refund exists. The HTTP route answers 422; the consumer of
// the personal emergency treats it as a normal result.
public class NoRefundDueException extends BusinessRuleViolation {
    public NoRefundDueException(String message) {
        super(message);
    }
}
