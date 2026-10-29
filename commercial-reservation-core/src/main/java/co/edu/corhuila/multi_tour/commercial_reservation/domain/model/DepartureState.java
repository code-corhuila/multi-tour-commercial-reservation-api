package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// The capacity row of one departure as the mirror keeps it: start, status and seats (HU-COM-006 plan D-07, D-08).
public record DepartureState(UUID departureId, UUID tenantId, UUID serviceId, Instant startsAt,
        DepartureStatus status, int effectiveCapacity, int heldSeats) {

    public SeatAvailability seats() {
        return new SeatAvailability(effectiveCapacity, heldSeats);
    }

    public DepartureState withStatus(DepartureStatus next) {
        return new DepartureState(departureId, tenantId, serviceId, startsAt, next, effectiveCapacity, heldSeats);
    }

    public DepartureState withEffectiveCapacity(int capacity) {
        return new DepartureState(departureId, tenantId, serviceId, startsAt, status, capacity, heldSeats);
    }
}
