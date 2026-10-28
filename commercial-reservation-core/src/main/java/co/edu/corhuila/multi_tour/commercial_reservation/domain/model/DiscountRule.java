package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

public final class DiscountRule {
    private final UUID ruleId, tenantId;
    private final String name;
    private final Instant validFrom, validTo, createdAt;
    private final Integer priority, percentageBasisPoints;
    private final Long amountCents;
    private final DiscountBase calculationBase;
    private DiscountStatus status;
    private Instant updatedAt;

    private DiscountRule(UUID id, UUID tenant, String name, Instant from, Instant to, Integer priority,
            DiscountBase base, Integer basisPoints, Long cents, DiscountStatus status, Instant created, Instant updated) {
        ruleId = id; tenantId = tenant; this.name = name; validFrom = from; validTo = to; this.priority = priority;
        calculationBase = base; percentageBasisPoints = basisPoints; amountCents = cents; this.status = status;
        createdAt = created; updatedAt = updated;
    }

    public static DiscountRule create(UUID id, UUID tenant, String name, Instant from, Instant to, Integer priority,
            DiscountBase base, Integer basisPoints, Long cents, Instant now) {
        return new DiscountRule(id, tenant, name, from, to, priority, base, basisPoints, cents, DiscountStatus.ACTIVE, now, now);
    }

    public static DiscountRule restore(UUID id, UUID tenant, String name, Instant from, Instant to, Integer priority,
            DiscountBase base, Integer basisPoints, Long cents, DiscountStatus status, Instant created, Instant updated) {
        return new DiscountRule(id, tenant, name, from, to, priority, base, basisPoints, cents, status, created, updated);
    }

    public void inactivate(UUID actor, Instant now) { }

    public UUID ruleId() { return ruleId; }
    public UUID tenantId() { return tenantId; }
    public String name() { return name; }
    public Instant validFrom() { return validFrom; }
    public Instant validTo() { return validTo; }
    public Integer priority() { return priority; }
    public DiscountBase calculationBase() { return calculationBase; }
    public Integer percentageBasisPoints() { return percentageBasisPoints; }
    public Long amountCents() { return amountCents; }
    public DiscountStatus status() { return status; }
    public boolean isActive() { return status == DiscountStatus.ACTIVE; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
