package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// domain-events.md: the cause and the seats released, and nothing that names a person (norm 5.3.11).
public record ReservationCancelled(UUID tenantId, UUID reservationId, CancellationCause cause, int releasedSeats,
        Instant occurredAt) implements PaymentEvent {
}
