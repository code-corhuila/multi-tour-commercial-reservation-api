package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CatalogService {

    private static final int MAX_NAME_LENGTH = 255;

    private final UUID serviceId;
    private final UUID tenantId;
    private final CatalogServiceType serviceType;
    private String commercialName;
    private CatalogStatus catalogStatus;
    private CommercialTerms terms;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<CatalogServiceEvent> pendingEvents = new ArrayList<>();

    private CatalogService(UUID serviceId, UUID tenantId, CatalogServiceType serviceType, String commercialName,
            CatalogStatus catalogStatus, CommercialTerms terms, Instant createdAt, Instant updatedAt) {
        this.serviceId = serviceId;
        this.tenantId = tenantId;
        this.serviceType = serviceType;
        this.commercialName = commercialName;
        this.catalogStatus = catalogStatus;
        this.terms = terms;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CatalogService register(UUID serviceId, UUID tenantId, CatalogServiceType serviceType,
            String commercialName, CommercialTerms terms, Instant now) {
        // A new item is ACTIVE: inferred, not confirmed by the product (02-domain DD-05).
        return new CatalogService(serviceId, tenantId, serviceType, requireName(commercialName),
                CatalogStatus.ACTIVE, coherentWith(serviceType, terms), now, now);
    }

    // Rebuilds a stored item without validating it: rows written under an earlier rule must stay readable,
    // and the invariants are checked again at the next transition that matters (reactivation, RN-03).
    public static CatalogService restore(UUID serviceId, UUID tenantId, CatalogServiceType serviceType,
            String commercialName, CatalogStatus catalogStatus, CommercialTerms terms, Instant createdAt,
            Instant updatedAt) {
        return new CatalogService(serviceId, tenantId, serviceType, commercialName, catalogStatus, terms,
                createdAt, updatedAt);
    }

    // One method per intent instead of a generic update: the adapter picks by permission, the aggregate
    // guarantees a caller of one intent cannot reach the other's fields (plan D-03).
    public void updateCommercialTerms(CommercialTermsChange change, Instant now) {
        CommercialTerms merged = CommercialTerms.of(
                change.baseRateCents() != null ? change.baseRateCents() : terms.baseRate().amount(),
                change.baseOperationalCostCents() != null
                        ? change.baseOperationalCostCents() : terms.baseOperationalCost().amount(),
                change.configuredCapacity() != null ? change.configuredCapacity() : terms.configuredCapacity(),
                change.transportRequired() != null ? change.transportRequired() : terms.transportRequired(),
                change.highRisk() != null ? change.highRisk() : terms.highRisk());
        this.terms = coherentWith(serviceType, merged);
        this.updatedAt = now;
    }

    public void updateDescriptiveInformation(String newCommercialName, Instant now) {
        this.commercialName = requireName(newCommercialName);
        this.updatedAt = now;
    }

    // Repeating a transition is a no-op that emits nothing: consumers must not act twice on a change
    // that did not happen (plan D-06).
    public void inactivate(UUID actorId, Instant now) {
        if (catalogStatus == CatalogStatus.INACTIVE) {
            return;
        }
        transitionTo(CatalogStatus.INACTIVE, now);
        pendingEvents.add(new CatalogServiceInactivated(tenantId, serviceId, CatalogStatus.ACTIVE,
                CatalogStatus.INACTIVE, actorId, now));
    }

    // Reactivation runs the registration invariants again instead of trusting what was stored: it is the
    // only way back into the offer, so it cannot be a way around a rule. Today the restrictions are exactly
    // those invariants; validity and held-seat capacity join them with RN-05 (T-06) and HU-COM-007.
    public void reactivate(UUID actorId, Instant now) {
        if (catalogStatus == CatalogStatus.ACTIVE) {
            return;
        }
        requireName(commercialName);
        this.terms = coherentWith(serviceType, terms);
        transitionTo(CatalogStatus.ACTIVE, now);
        pendingEvents.add(new CatalogServiceReactivated(tenantId, serviceId, CatalogStatus.INACTIVE,
                CatalogStatus.ACTIVE, actorId, now));
    }

    // Drained by whoever persists the item, so the events and the state change commit together (plan D-07).
    public List<CatalogServiceEvent> pullEvents() {
        List<CatalogServiceEvent> drained = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return drained;
    }

    private void transitionTo(CatalogStatus status, Instant now) {
        this.catalogStatus = status;
        this.updatedAt = now;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new DomainException("commercialName", "commercialName must have 1 to 255 characters");
        }
        return name;
    }

    // Kept in one place so registration, update and reactivation (RN-03) cannot drift apart.
    private static CommercialTerms coherentWith(CatalogServiceType type, CommercialTerms terms) {
        if (type == CatalogServiceType.TOUR) {
            if (terms.configuredCapacity() == null) {
                throw new DomainException("configuredCapacity", "a tour requires configuredCapacity");
            }
            return terms;
        }
        // The contract fixes transportRequired to false for transportation; a transport item is the transport.
        return new CommercialTerms(terms.baseRate(), terms.baseOperationalCost(), terms.configuredCapacity(),
                false, terms.highRisk());
    }

    public UUID serviceId() { return serviceId; }
    public UUID tenantId() { return tenantId; }
    public CatalogServiceType serviceType() { return serviceType; }
    public String commercialName() { return commercialName; }
    public CatalogStatus catalogStatus() { return catalogStatus; }
    public CommercialTerms terms() { return terms; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
