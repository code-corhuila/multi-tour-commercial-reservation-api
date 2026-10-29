package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.time.Instant;
import java.util.UUID;

// The five departure events this service consumes (ADR-004 D4); the transport that delivers them is not built here.
public sealed interface SalidaEvent {

    UUID eventId();

    UUID tenantId();

    UUID departureId();

    record Scheduled(UUID eventId, UUID tenantId, UUID departureId, UUID serviceId, Instant startsAt)
            implements SalidaEvent {
    }

    record ExecutionStarted(UUID eventId, UUID tenantId, UUID departureId) implements SalidaEvent {
    }

    record Finalized(UUID eventId, UUID tenantId, UUID departureId) implements SalidaEvent {
    }

    record Cancelled(UUID eventId, UUID tenantId, UUID departureId) implements SalidaEvent {
    }

    record VehiclesChanged(UUID eventId, UUID tenantId, UUID departureId, int vehicleTotalCapacity)
            implements SalidaEvent {
    }
}
