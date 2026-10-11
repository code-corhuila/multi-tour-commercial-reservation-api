package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

public record CatalogServiceInactivated(UUID tenantId, UUID serviceId, CatalogStatus previousStatus,
        CatalogStatus newStatus, UUID actorId, Instant occurredAt) implements CatalogServiceEvent {
}
