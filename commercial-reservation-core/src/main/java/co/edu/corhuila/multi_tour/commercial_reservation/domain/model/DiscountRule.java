package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class DiscountRule {
    private final UUID ruleId, tenantId;
    private final Instant createdAt;
    private String name;
    private LocalDate validFrom, validTo;
    private ServiceCoverage serviceCoverage;
    private Long capCents;
    private Integer priority, percentageBasisPoints;
    private Long amountCents;
    private DiscountBase calculationBase;
    private boolean stackable;
    private DiscountStatus status;
    private Instant updatedAt;

    private DiscountRule(UUID id, UUID tenant, String name, LocalDate from, LocalDate to, Integer priority,
            DiscountBase base, Integer basisPoints, Long cents, ServiceCoverage coverage, Long cap, boolean stackable,
            DiscountStatus status, Instant created, Instant updated) {
        ruleId = id; tenantId = tenant; this.name = name; validFrom = from; validTo = to; this.priority = priority;
        calculationBase = base; percentageBasisPoints = basisPoints; amountCents = cents; serviceCoverage = coverage; capCents = cap;
        this.stackable = stackable; this.status = status; createdAt = created; updatedAt = updated;
    }

    public static DiscountRule create(UUID id, UUID tenant, String name, LocalDate from, LocalDate to, Integer priority,
            DiscountBase base, Integer basisPoints, Long cents, ServiceCoverage coverage, Long cap, Instant now) {
        return create(id, tenant, name, from, to, priority, base, basisPoints, cents, coverage, cap, true, now);
    }

    public static DiscountRule create(UUID id, UUID tenant, String name, LocalDate from, LocalDate to, Integer priority,
            DiscountBase base, Integer basisPoints, Long cents, ServiceCoverage coverage, Long cap, boolean stackable, Instant now) {
        required(id, "ruleId"); required(tenant, "tenantId"); required(now, "occurredAt");
        validateFields(name, from, to, priority, base, basisPoints, cents, coverage, cap);
        return new DiscountRule(id, tenant, name, from, to, priority, base, basisPoints, cents, coverage, cap, stackable,
                DiscountStatus.ACTIVE, now, now);
    }

    // The rules of a creation (AC-03.3, AC-03.4); an update must satisfy them again on the merged rule (AC-03.16).
    private static void validateFields(String name, LocalDate from, LocalDate to, Integer priority, DiscountBase base,
            Integer basisPoints, Long cents, ServiceCoverage coverage, Long cap) {
        if (name == null || name.isBlank()) throw new DomainException("name", "name is required");
        required(priority, "priority"); required(base, "calculationBase");
        if (priority < 1) throw new DomainException("priority", "priority must be at least 1");
        required(from, "validFrom"); required(to, "validTo");
        if (from.isAfter(to)) throw new DomainException("validFrom", "validFrom cannot be after validTo");
        validateValue(basisPoints, cents);
        required(coverage, "serviceCoverage");
        if (cap != null && cap <= 0) throw new DomainException("capCents", "capCents must be above zero");
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

    public static DiscountRule restore(UUID id, UUID tenant, String name, LocalDate from, LocalDate to, Integer priority,
            DiscountBase base, Integer basisPoints, Long cents, ServiceCoverage coverage, Long cap, DiscountStatus status, Instant created, Instant updated) {
        return restore(id, tenant, name, from, to, priority, base, basisPoints, cents, coverage, cap, true, status, created, updated);
    }

    public static DiscountRule restore(UUID id, UUID tenant, String name, LocalDate from, LocalDate to, Integer priority,
            DiscountBase base, Integer basisPoints, Long cents, ServiceCoverage coverage, Long cap, boolean stackable,
            DiscountStatus status, Instant created, Instant updated) {
        return new DiscountRule(id, tenant, name, from, to, priority, base, basisPoints, cents, coverage, cap, stackable, status, created, updated);
    }

    // A transition, never a deletion (RN-13). A repeated one is not a new fact and keeps the audit time.
    public void inactivate(UUID actor, Instant now) {
        required(actor, "actorId"); required(now, "occurredAt");
        if (status == DiscountStatus.INACTIVE) return;
        status = DiscountStatus.INACTIVE; updatedAt = now;
    }

    // The mirror of inactivate (AC-03.17).
    public void reactivate(UUID actor, Instant now) {
        required(actor, "actorId"); required(now, "occurredAt");
        if (status == DiscountStatus.ACTIVE) return;
        status = DiscountStatus.ACTIVE; updatedAt = now;
    }

    // Merge, revalidate as a creation, and change only if something differs (AC-03.16). Identity, tenant and status are never touched.
    // Giving a percentage clears the amount and the other way round, unless the patch states both (then the rules reject it).
    public boolean update(UUID actor, DiscountRulePatch patch, Instant now) {
        required(actor, "actorId"); required(now, "occurredAt"); required(patch, "patch");
        String newName = patch.name().or(name);
        LocalDate newFrom = patch.validFrom().or(validFrom), newTo = patch.validTo().or(validTo);
        Integer newPriority = patch.priority().or(priority);
        DiscountBase newBase = patch.calculationBase().or(calculationBase);
        Integer newBasisPoints = patch.percentageBasisPoints().or(percentageBasisPoints);
        Long newCents = patch.amountCents().or(amountCents);
        if (patch.percentageBasisPoints().present() && newBasisPoints != null && !patch.amountCents().present()) newCents = null;
        if (patch.amountCents().present() && newCents != null && !patch.percentageBasisPoints().present()) newBasisPoints = null;
        ServiceCoverage newCoverage = patch.serviceCoverage().or(serviceCoverage);
        Long newCap = patch.capCents().or(capCents);
        boolean newStackable = patch.stackable().present() ? Boolean.TRUE.equals(patch.stackable().value()) : stackable;
        validateFields(newName, newFrom, newTo, newPriority, newBase, newBasisPoints, newCents, newCoverage, newCap);
        if (Objects.equals(newName, name) && newFrom.equals(validFrom) && newTo.equals(validTo) && newPriority.equals(priority)
                && newBase == calculationBase && Objects.equals(newBasisPoints, percentageBasisPoints)
                && Objects.equals(newCents, amountCents) && newCoverage.equals(serviceCoverage)
                && Objects.equals(newCap, capCents) && newStackable == stackable) return false;
        name = newName; validFrom = newFrom; validTo = newTo; priority = newPriority; calculationBase = newBase;
        percentageBasisPoints = newBasisPoints; amountCents = newCents; serviceCoverage = newCoverage; capCents = newCap;
        stackable = newStackable; updatedAt = now;
        return true;
    }

    public UUID ruleId() { return ruleId; }
    public UUID tenantId() { return tenantId; }
    public String name() { return name; }
    public LocalDate validFrom() { return validFrom; }
    public LocalDate validTo() { return validTo; }
    public Integer priority() { return priority; }
    public DiscountBase calculationBase() { return calculationBase; }
    public Integer percentageBasisPoints() { return percentageBasisPoints; }
    public Long amountCents() { return amountCents; }
    public ServiceCoverage serviceCoverage() { return serviceCoverage; }
    public Long capCents() { return capCents; }
    public boolean stackable() { return stackable; }
    public DiscountStatus status() { return status; }
    public boolean isActive() { return status == DiscountStatus.ACTIVE; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
