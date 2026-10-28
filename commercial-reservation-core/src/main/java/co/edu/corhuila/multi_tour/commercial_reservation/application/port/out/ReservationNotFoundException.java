package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// Also raised for a reservation of another tenant, so its existence is not revealed.
public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(UUID reservationId) { super("reservation " + reservationId + " not found"); }
}
