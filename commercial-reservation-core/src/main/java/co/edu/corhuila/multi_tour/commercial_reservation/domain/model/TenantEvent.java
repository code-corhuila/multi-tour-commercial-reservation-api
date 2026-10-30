package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// One tenant event as the route understood it. The payload parts may be null: each type needs only its own (RN-TSC-04).
public record TenantEvent(UUID eventId, TenantEventType type, UUID tenantId, Instant occurredAt, String identifier,
        TenantStatus status, String timeZone, String country) {
}
