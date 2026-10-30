package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ReservationConfirmed(UUID tenantId, UUID reservationId, PaymentStatus paymentStatus, int allocatedCapacity,
        Instant occurredAt) implements PaymentEvent {
}
