package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// References only (plan D-10, RN-13): no document, contact or medical data.
public record CustomerRegistered(UUID tenantId, UUID customerId, UUID actorId, Instant occurredAt) {
}
