package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// The capacity row of one departure as the mirror keeps it: start, status and seats (HU-COM-006 plan D-07, D-08), and the
// occurredAt of the last event applied (DP-16, DD-8). A row born before the marker has none (null).
public record DepartureState(UUID departureId, UUID tenantId, UUID serviceId, Instant startsAt,
        DepartureStatus status, int effectiveCapacity, int heldSeats, Instant lastEventOccurredAt) {

    public DepartureState(UUID departureId, UUID tenantId, UUID serviceId, Instant startsAt,
            DepartureStatus status, int effectiveCapacity, int heldSeats) {
        this(departureId, tenantId, serviceId, startsAt, status, effectiveCapacity, heldSeats, null);
    }

    public SeatAvailability seats() {
        return new SeatAvailability(effectiveCapacity, heldSeats);
    }

    public DepartureState withStatus(DepartureStatus next) {
        return new DepartureState(departureId, tenantId, serviceId, startsAt, next, effectiveCapacity, heldSeats,
                lastEventOccurredAt);
    }

    public DepartureState withEffectiveCapacity(int capacity) {
        return new DepartureState(departureId, tenantId, serviceId, startsAt, status, capacity, heldSeats,
                lastEventOccurredAt);
    }

    // The newest instant between the marker and the event: the marker never goes back.
    public DepartureState appliedAt(Instant occurredAt) {
        Instant newest = lastEventOccurredAt == null || occurredAt.isAfter(lastEventOccurredAt) ? occurredAt : lastEventOccurredAt;
        return new DepartureState(departureId, tenantId, serviceId, startsAt, status, effectiveCapacity, heldSeats, newest);
    }

    // True when the row already applied an event that happened after this one.
    public boolean isNewerThan(Instant occurredAt) {
        return lastEventOccurredAt != null && lastEventOccurredAt.isAfter(occurredAt);
    }
}
