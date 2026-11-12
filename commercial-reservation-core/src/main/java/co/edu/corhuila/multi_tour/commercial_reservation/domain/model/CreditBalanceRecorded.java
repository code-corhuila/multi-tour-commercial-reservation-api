package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// HU-COM-017 AC-17.17: a credit balance was recorded on its source reservation. It carries identifiers, the amount and the origin, never a name, a reason or a
// document. The actor is the person who decided it; the system recording of an excess has none, so the outbox takes the person of the attempt.
public record CreditBalanceRecorded(UUID tenantId, UUID reservationId, Instant occurredAt, UUID creditBalanceId, Cents amount, CreditOrigin origin, UUID actorId)
        implements PaymentEvent {
}
