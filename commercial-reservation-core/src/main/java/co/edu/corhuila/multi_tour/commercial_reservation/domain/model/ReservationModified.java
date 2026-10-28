package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// Candidate name of domain-events.md, not an invented type (G-F): the additional discount recalculates
// the reservation, with reason, authorizer and order in the payload.
public record ReservationModified(UUID tenantId, UUID reservationId, Cents previousSubtotal, Cents newSubtotal,
        String reason, UUID authorizerId, int order, Instant occurredAt) {
}
