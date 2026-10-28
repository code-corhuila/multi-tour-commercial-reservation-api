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
        required(id, "ruleId"); required(tenant, "tenantId"); required(now, "occurredAt");
        if (name == null || name.isBlank()) throw new DomainException("name", "name is required");
        required(priority, "priority"); required(base, "calculationBase");
        required(from, "validFrom"); required(to, "validTo");
        if (from.isAfter(to)) throw new DomainException("validFrom", "validFrom cannot be after validTo");
        validateValue(basisPoints, cents);
        return new DiscountRule(id, tenant, name, from, to, priority, base, basisPoints, cents, DiscountStatus.ACTIVE, now, now);
    }

    // Exactly one of the two (RN-08); no decimal crosses a port (plan D-07).
    private static void validateValue(Integer basisPoints, Long cents) {
        if (basisPoints != null && cents != null)
            throw new DomainException("amountCents", "a rule is a percentage or a fixed amount, never both");
        if (basisPoints == null && cents == null)
            throw new DomainException("percentageBasisPoints", "a percentage or a fixed amount is required");
        if (basisPoints != null && (basisPoints < 1 || basisPoints > 10_000))
            throw new DomainException("percentageBasisPoints", "percentageBasisPoints must be from 1 to 10000");
        if (cents != null && Cents.of("amountCents", cents).amount() == 0)
            throw new DomainException("amountCents", "amountCents must be above zero");
    }

    private static <T> T required(T value, String field) {
        if (value == null) throw new DomainException(field, field + " is required");
        return value;
    }

    public static DiscountRule restore(UUID id, UUID tenant, String name, Instant from, Instant to, Integer priority,
            DiscountBase base, Integer basisPoints, Long cents, DiscountStatus status, Instant created, Instant updated) {
        return new DiscountRule(id, tenant, name, from, to, priority, base, basisPoints, cents, status, created, updated);
    }

    // A transition, never a deletion (RN-13). A repeated one is not a new fact and keeps the audit time.
    public void inactivate(UUID actor, Instant now) {
        required(actor, "actorId"); required(now, "occurredAt");
        if (status == DiscountStatus.INACTIVE) return;
        status = DiscountStatus.INACTIVE; updatedAt = now;
    }

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
