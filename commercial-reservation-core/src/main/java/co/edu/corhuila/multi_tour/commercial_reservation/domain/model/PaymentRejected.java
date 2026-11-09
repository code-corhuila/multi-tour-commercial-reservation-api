package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// The proof (attempt) id lets a consumer correlate and deduplicate. The free-text reason of the reviewer may hold personal data, so it never
// travels in the event: it stays on the attempt row (decision_reason) and is read on the proof routes.
public record PaymentRejected(UUID tenantId, UUID reservationId, UUID attemptId, Cents pendingBalance, UUID validatorId,
        Instant occurredAt) implements PaymentEvent {
}
