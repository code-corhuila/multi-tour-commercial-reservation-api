package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// A well-formed request refused by a rule of the reason catalogue (duplicate text, last enabled reason).
// It stays a DomainException so existing callers keep working; the HTTP adapter answers it as a business rule.
public final class ReasonRuleViolation extends DomainException {
    public ReasonRuleViolation(String message) {
        super("reason", message);
    }
}
