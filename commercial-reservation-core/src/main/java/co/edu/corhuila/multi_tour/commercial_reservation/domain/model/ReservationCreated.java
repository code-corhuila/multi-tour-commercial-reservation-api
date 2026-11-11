package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// Domain event of the creation (domain-events.md): references and values only, no person data (RN-12). The companions,
// the documents and the contact of the customer are never part of it.
public record ReservationCreated(UUID tenantId, UUID reservationId, UUID customerId, UUID serviceId, Cents projectedValue,
        Cents finalValue, UUID actorId, Instant occurredAt) {

    public static ReservationCreated of(Reservation reservation) {
        return new ReservationCreated(reservation.tenantId(), reservation.reservationId(), reservation.customerId(),
                reservation.service().serviceId(), reservation.projectedValue(), reservation.finalValue(),
                reservation.createdBy(), reservation.createdAt());
    }
}
