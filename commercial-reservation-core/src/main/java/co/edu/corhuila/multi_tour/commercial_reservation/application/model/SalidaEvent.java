package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.time.Instant;
import java.util.UUID;

// The six departure events this service consumes (ADR-004 D4). occurredAt is the instant of the envelope: the mirror
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

    // cancelledAt is the instant the payload carries (the same one the saga cancel-departure uses); null when the payload has none, and then the envelope occurredAt counts (spec 023, DP-23.2).
    record Cancelled(UUID eventId, UUID tenantId, UUID departureId, Instant cancelledAt, Instant occurredAt) implements SalidaEvent {
        public Cancelled(UUID eventId, UUID tenantId, UUID departureId, Instant occurredAt) {
            this(eventId, tenantId, departureId, null, occurredAt);
        }

        public Instant cancellationInstant() {
            return cancelledAt != null ? cancelledAt : occurredAt;
        }
    }

    // SalidaRescheduled (spec 027): the new start and estimated end, and the service of the departure as the producer sends it.
    record Rescheduled(UUID eventId, UUID tenantId, UUID departureId, UUID serviceId, Instant startsAt, Instant estimatedEndsAt, Instant occurredAt)
            implements SalidaEvent {
    }

    record VehiclesChanged(UUID eventId, UUID tenantId, UUID departureId, int vehicleTotalCapacity, Instant occurredAt)
            implements SalidaEvent {
    }
}
