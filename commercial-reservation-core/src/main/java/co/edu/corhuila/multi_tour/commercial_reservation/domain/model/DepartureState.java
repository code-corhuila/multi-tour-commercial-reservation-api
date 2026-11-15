package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// The capacity row of one departure as the mirror keeps it: start, status and seats (HU-COM-006 plan D-07, D-08), and the
// occurredAt of the last event applied (DP-16, DD-8). A row born before the marker has none (null).
// vehicleCapacityCeiling is the total capacity of the assigned vehicles of the last vehicle event (ND-08, DP-D6-8); it is null while
// no vehicle event arrived, and then the effective capacity is not recomputed from it.
public record DepartureState(UUID departureId, UUID tenantId, UUID serviceId, Instant startsAt,
        DepartureStatus status, int effectiveCapacity, int heldSeats, Instant lastEventOccurredAt,
        Integer vehicleCapacityCeiling) {

    public DepartureState(UUID departureId, UUID tenantId, UUID serviceId, Instant startsAt,
            DepartureStatus status, int effectiveCapacity, int heldSeats, Instant lastEventOccurredAt) {
        this(departureId, tenantId, serviceId, startsAt, status, effectiveCapacity, heldSeats, lastEventOccurredAt, null);
    }

    public DepartureState(UUID departureId, UUID tenantId, UUID serviceId, Instant startsAt,
            DepartureStatus status, int effectiveCapacity, int heldSeats) {
        this(departureId, tenantId, serviceId, startsAt, status, effectiveCapacity, heldSeats, null, null);
    }

    // RN-SAL-001 and H8: the lower of the tour capacity and the vehicle total, never below the seats already held.
    public static int effectiveCapacityOf(int tourCapacity, int vehicleTotal, int heldSeats) {
        return Math.max(Math.min(tourCapacity, vehicleTotal), heldSeats);
    }

    public boolean isOpen() {
        return status != DepartureStatus.FINALIZED && status != DepartureStatus.CANCELLED;
    }

    public SeatAvailability seats() {
        return new SeatAvailability(effectiveCapacity, heldSeats);
    }

    // The row after a change of the tour capacity (RN-SAL-001: the lower of the tour and the transport only where transport applies).
    // With a stored vehicle total it is the lower of both, never below the held seats. With no total and no mandatory transport the tour
    // capacity alone binds it (never below the held seats). With mandatory transport and no vehicle event yet (G-VEH) it stays as it is,
    // and so does a final departure.
    public DepartureState withTourCapacity(int tourCapacity, boolean transportRequired) {
        if (!isOpen()) {
            return this;
        }
        if (vehicleCapacityCeiling != null) {
            return withEffectiveCapacity(effectiveCapacityOf(tourCapacity, vehicleCapacityCeiling, heldSeats));
        }
        return transportRequired ? this : withEffectiveCapacity(Math.max(tourCapacity, heldSeats));
    }

    public DepartureState withVehicleCapacityCeiling(int total) {
        return new DepartureState(departureId, tenantId, serviceId, startsAt, status, effectiveCapacity, heldSeats,
                lastEventOccurredAt, total);
    }

    public DepartureState withStatus(DepartureStatus next) {
        return new DepartureState(departureId, tenantId, serviceId, startsAt, next, effectiveCapacity, heldSeats,
                lastEventOccurredAt, vehicleCapacityCeiling);
    }

    public DepartureState withEffectiveCapacity(int capacity) {
        return new DepartureState(departureId, tenantId, serviceId, startsAt, status, capacity, heldSeats,
                lastEventOccurredAt, vehicleCapacityCeiling);
    }

    // The newest instant between the marker and the event: the marker never goes back.
    public DepartureState appliedAt(Instant occurredAt) {
        Instant newest = lastEventOccurredAt == null || occurredAt.isAfter(lastEventOccurredAt) ? occurredAt : lastEventOccurredAt;
        return new DepartureState(departureId, tenantId, serviceId, startsAt, status, effectiveCapacity, heldSeats, newest,
                vehicleCapacityCeiling);
    }

    // True when the row already applied an event that happened after this one.
    public boolean isNewerThan(Instant occurredAt) {
        return lastEventOccurredAt != null && lastEventOccurredAt.isAfter(occurredAt);
    }
}
