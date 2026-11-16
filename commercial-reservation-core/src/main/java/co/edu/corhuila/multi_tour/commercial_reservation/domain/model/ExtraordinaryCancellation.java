package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// Spec 025: the fact that operations records when one reservation in execution is cancelled for an emergency. It carries ids and an instant only: the justification text
// of the event is read by the adapter to check its length and is dropped there, so it never becomes part of this model (data protection).
public record ExtraordinaryCancellation(UUID eventId, UUID tenantId, UUID salidaId, UUID reservationId, Instant occurredAt) {

    public ExtraordinaryCancellation {
        if (eventId == null || tenantId == null || salidaId == null || reservationId == null || occurredAt == null)
            throw new DomainException("extraordinaryCancellation", "the event, the tenant, the departure, the reservation and the instant are required");
    }
}
