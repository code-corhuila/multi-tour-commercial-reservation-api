package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// OutboxEvent of the contract: the envelope of ADR-004 D4 and the delivery state. nextAttemptAt is the instant of the event until a first failure.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutboxEventResponse(UUID eventId, String eventType, int eventVersion, UUID tenantId, UUID aggregateId, Map<String, Object> payload,
        String correlationId, Instant occurredAt, Instant publishedAt, String status, int attempts, String lastError, Instant nextAttemptAt) {

    static OutboxEventResponse of(OutboxEventView view) {
        Instant next = view.nextAttemptAt() != null ? view.nextAttemptAt() : view.status().name().equals("PENDING") ? view.occurredAt() : null;
        return new OutboxEventResponse(view.eventId(), view.eventType(), view.eventVersion(), view.tenantId(), view.aggregateId(), view.payload(),
                view.correlationId(), view.occurredAt(), view.publishedAt(), view.status().name(), view.attempts(), view.lastError(), next);
    }
}
