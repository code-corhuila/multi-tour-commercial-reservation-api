package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AssociatedEstablishment {
    private final UUID associationId, tenantId;
    private final EstablishmentType establishmentType;
    private final String commercialName, commercialInformation, imageReference;
    private AssociationStatus associationStatus;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<EstablishmentAssociationEvent> pendingEvents = new ArrayList<>();

    private AssociatedEstablishment(UUID id, UUID tenant, EstablishmentType type, String name,
            String information, String image, AssociationStatus status, Instant created, Instant updated) {
        associationId = required(id, "associationId"); tenantId = required(tenant, "tenantId");
        establishmentType = required(type, "establishmentType"); commercialName = name;
        commercialInformation = information; imageReference = image;
        associationStatus = required(status, "associationStatus");
        createdAt = required(created, "createdAt"); updatedAt = required(updated, "updatedAt");
    }

    public static AssociatedEstablishment register(UUID id, UUID tenant, EstablishmentType type,
            String name, String information, String image, UUID actor, Instant now) {
        validate(type, name, information, image);
        required(actor, "actorId"); required(now, "occurredAt");
        var item = new AssociatedEstablishment(id, tenant, type, name, information, image,
                AssociationStatus.ACTIVE, now, now);
        item.pendingEvents.add(new EstablishmentAssociationPublished(tenant, id, actor, now));
        return item;
    }

    // Stored historical information remains readable; reactivation checks its eligibility again.
    public static AssociatedEstablishment restore(UUID id, UUID tenant, EstablishmentType type,
            String name, String information, String image, AssociationStatus status, Instant created, Instant updated) {
        return new AssociatedEstablishment(id, tenant, type, name, information, image, status, created, updated);
    }

    public void inactivate(UUID actor, Instant now) { transition(AssociationStatus.INACTIVE, actor, now); }
    public void reactivate(UUID actor, Instant now) { transition(AssociationStatus.ACTIVE, actor, now); }

    private void transition(AssociationStatus next, UUID actor, Instant now) {
        // A repeated intention is not a new business fact and must not advance its audit time.
        if (associationStatus == next) return;
        required(actor, "actorId"); required(now, "occurredAt");
        if (next == AssociationStatus.ACTIVE) {
            try { validate(establishmentType, commercialName, commercialInformation, imageReference); }
            catch (DomainException invalid) {
                throw new BusinessRuleViolation("association is not eligible for reactivation", invalid);
            }
        }
        associationStatus = next; updatedAt = now;
        pendingEvents.add(next == AssociationStatus.ACTIVE
                ? new EstablishmentAssociationReactivated(tenantId, associationId, actor, now)
                : new EstablishmentAssociationInactivated(tenantId, associationId, actor, now));
    }

    // The repository drains these together with the state, within its transaction boundary.
    public List<EstablishmentAssociationEvent> pullEvents() {
        var events = List.copyOf(pendingEvents); pendingEvents.clear(); return events;
    }

    private static void validate(EstablishmentType type, String name, String information, String image) {
        required(type, "establishmentType"); text(name, "commercialName");
        if (name.length() > 255) throw new DomainException("commercialName", "commercialName must have 1 to 255 characters");
        text(information, "commercialInformation"); text(image, "imageReference");
    }
    private static void text(String value, String field) {
        if (value == null || value.isBlank()) throw new DomainException(field, field + " is required");
    }
    private static <T> T required(T value, String field) {
        if (value == null) throw new DomainException(field, field + " is required");
        return value;
    }

    public UUID associationId() { return associationId; }
    public UUID tenantId() { return tenantId; }
    public EstablishmentType establishmentType() { return establishmentType; }
    public String commercialName() { return commercialName; }
    public String commercialInformation() { return commercialInformation; }
    public String imageReference() { return imageReference; }
    public AssociationStatus associationStatus() { return associationStatus; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
