package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.UUID;

// One general rule in force at creation, kept as it was (RN-RES-003): the type and value of the rule are copied, so
// the line can be explained without the rule, and a line whose amount came out zero is kept too.
public record FixedDiscount(int order, UUID ruleId, String ruleName, DiscountType type, Integer percentageBasisPoints,
        Long amountCents, DiscountBase calculationBase, Cents base, Cents amount, Cents subtotal, Long capCents) {

    public FixedDiscount {
        if (order < 1 || ruleId == null || ruleName == null || type == null || calculationBase == null) {
            throw new DomainException("discount", "a fixed discount needs its order, rule, type and base");
        }
        // The subtotal is the base minus the amount, never below zero.
        new DiscountLine(base, amount, subtotal);
    }
}
