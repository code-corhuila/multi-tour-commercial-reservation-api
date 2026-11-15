package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// References only (RN-08, RN-13): who, which customer, when and the optional reason; never the data itself.
public record SensitiveDataAccessed(UUID tenantId, UUID customerId, UUID actorId, String reason, Instant occurredAt) {
}
