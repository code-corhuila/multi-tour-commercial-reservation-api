package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Instant;
import java.util.UUID;

// HU-COM-018 AC-18.8: the incident the saga reports for one reservation: which departure was cancelled and when (the instant the 24 hours count from).
public record OpenReschedulingCommand(UUID tenantId, UUID reservationId, UUID departureId, Instant cancelledAt) {

    public OpenReschedulingCommand {
        if (tenantId == null || reservationId == null || departureId == null || cancelledAt == null)
            throw new DomainException("rescheduling", "the tenant, the reservation, the departure and the cancellation instant are required");
    }
}
