package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// previous is null on creation. Delivery through the outbox belongs to the adapters (plan D-10).
public record DiscountRuleChanged(UUID tenantId, UUID ruleId, DiscountRuleState previous, DiscountRuleState current,
        UUID actorId, Instant occurredAt) {
}
