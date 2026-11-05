package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// An event of the outbox with the envelope of ADR-004 D4 and its delivery state, as job 7 reads it. The payload is the closed key list of the event: no
// name, e-mail, document, phone, amount, reason text or file reference. eventVersion is derived (DP-D15-5); nextAttemptAt is null until a first failure.
public record OutboxEventView(UUID eventId, String eventType, int eventVersion, UUID tenantId, UUID aggregateId, Map<String, Object> payload,
        String correlationId, Instant occurredAt, Instant publishedAt, OutboxStatus status, int attempts, String lastError, Instant nextAttemptAt) {
}
