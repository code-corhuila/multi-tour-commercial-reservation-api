package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// The reason is the one free-text field of the payment events (domain-events.md, db DP-D10-5): it may hold a name, so no log prints it.
public record PaymentRejected(UUID tenantId, UUID reservationId, String reason, Cents pendingBalance, UUID validatorId,
        Instant occurredAt) implements PaymentEvent {
}
