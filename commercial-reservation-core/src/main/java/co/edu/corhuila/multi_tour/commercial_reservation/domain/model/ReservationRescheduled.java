package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// domain-events.md, plan D-08: the agreed change, identifiers and instants only (the evidence and the channel of an outside answer never travel, norm 5.3.11). linkedReservationId is null.
public record ReservationRescheduled(UUID tenantId, UUID reservationId, UUID reschedulingRecordId, UUID linkedReservationId, UUID originalSalidaId, UUID agreedSalidaId, Instant agreedStartAt,
        UUID approvedBy, Instant occurredAt) implements PaymentEvent {
}
