package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// The payload of 02-domain/domain-events.md; delivery through the outbox belongs to the adapters (plan D-07).
public sealed interface CatalogServiceEvent permits CatalogServiceInactivated, CatalogServiceReactivated {

    UUID tenantId();

    UUID serviceId();

    CatalogStatus previousStatus();

    CatalogStatus newStatus();

    UUID actorId();

    Instant occurredAt();
}
