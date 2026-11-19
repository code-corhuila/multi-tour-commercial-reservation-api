package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// Spec 024: the fact that operations records when someone of a reservation in execution cannot continue. Ids, a count and an instant: the detail of the emergency
// never reaches this service (it can name health facts) and nothing here is personal data.
public record PersonalEmergency(UUID eventId, UUID tenantId, UUID salidaId, UUID reservationId, int affected, Instant occurredAt) {

    public PersonalEmergency {
        if (eventId == null || tenantId == null || salidaId == null || reservationId == null || occurredAt == null)
            throw new DomainException("personalEmergency", "the event, the tenant, the departure, the reservation and the instant are required");
        if (affected < 1) throw new DomainException("affected", "affected must be an integer of 1 or more");
    }
}
