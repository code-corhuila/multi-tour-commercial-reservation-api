package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// The full state of a rule at one moment: the audit keeps both sides of a change (plan D-10).
public record DiscountRuleState(UUID ruleId, UUID tenantId, String name, Instant validFrom, Instant validTo,
        Integer priority, DiscountBase calculationBase, Integer percentageBasisPoints, Long amountCents,
        DiscountStatus status, Instant createdAt, Instant updatedAt) {

    public static DiscountRuleState of(DiscountRule rule) {
        return new DiscountRuleState(rule.ruleId(), rule.tenantId(), rule.name(), rule.validFrom(), rule.validTo(),
                rule.priority(), rule.calculationBase(), rule.percentageBasisPoints(), rule.amountCents(),
                rule.status(), rule.createdAt(), rule.updatedAt());
    }
}
