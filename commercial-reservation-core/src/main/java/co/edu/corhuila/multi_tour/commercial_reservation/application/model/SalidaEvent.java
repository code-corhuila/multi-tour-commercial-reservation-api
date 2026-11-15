package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.time.Instant;
import java.util.UUID;

// The five departure events this service consumes (ADR-004 D4). occurredAt is the instant of the envelope: the mirror
// stores it as the marker of the last event applied (DP-16, DD-8).
public sealed interface SalidaEvent {

    UUID eventId();

    UUID tenantId();

    UUID departureId();

    Instant occurredAt();

    record Scheduled(UUID eventId, UUID tenantId, UUID departureId, UUID serviceId, Instant startsAt, Instant occurredAt)
            implements SalidaEvent {
    }

    record ExecutionStarted(UUID eventId, UUID tenantId, UUID departureId, Instant occurredAt) implements SalidaEvent {
    }

    record Finalized(UUID eventId, UUID tenantId, UUID departureId, Instant occurredAt) implements SalidaEvent {
    }

    record Cancelled(UUID eventId, UUID tenantId, UUID departureId, Instant occurredAt) implements SalidaEvent {
    }

    record VehiclesChanged(UUID eventId, UUID tenantId, UUID departureId, int vehicleTotalCapacity, Instant occurredAt)
            implements SalidaEvent {
    }
}
