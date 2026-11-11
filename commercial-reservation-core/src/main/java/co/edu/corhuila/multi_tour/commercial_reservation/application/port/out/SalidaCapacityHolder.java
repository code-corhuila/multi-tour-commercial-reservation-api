package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// One conditional update of the capacity row, held + requested within the effective capacity (ADR-004 D4, plan D-05):
// never a read followed by a write. False means no availability.
public interface SalidaCapacityHolder {

    boolean hold(UUID tenantId, UUID salidaId, int requested);

    // The open allocation of the reservation goes back to the departure by conditional update; returns the seats released,
    // zero when nothing was held (010 G-I, plan D-10).
    int release(UUID tenantId, UUID reservationId);

    // HU-COM-012 D-05: fewer travellers give back only their seats, in the caller's transaction; returns the seats released.
    default int releaseSeats(UUID tenantId, UUID reservationId, int seats) {
        throw new UnsupportedOperationException("the partial release needs the adapter override");
    }

    // HU-COM-012 D-05: a date change holds the new departure and releases the old one as one operation; false when the new one has no room.
    default boolean move(UUID tenantId, UUID reservationId, UUID fromSalidaId, UUID toSalidaId, int seats) {
        throw new UnsupportedOperationException("the move needs the adapter override");
    }

    // HU-COM-018 D-04: the temporary hold of a rescheduling proposal (allocation kind REPROGRAMMING_HOLD) in the proposed departure, by the same conditional update; false when it has no room.
    default boolean holdForRescheduling(UUID tenantId, UUID reservationId, UUID salidaId, int seats) {
        throw new UnsupportedOperationException("the rescheduling hold needs the adapter override");
    }
}
