package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// The first payment holds the seats of the reservation (RN-RES-007). The kind is the one allocation kind of the db.
public record CapacityAllocated(UUID tenantId, UUID reservationId, UUID serviceId, UUID salidaId, String allocationKind,
        Instant occurredAt) implements PaymentEvent {
    public static final String RESERVATION_KIND = "RESERVATION";
}
