package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// Also raised for a rule of another tenant: a 403 would reveal that it exists (RN-12).
public class DiscountRuleNotFoundException extends RuntimeException {
    public DiscountRuleNotFoundException(UUID ruleId) { super("discount rule " + ruleId + " not found"); }
}
