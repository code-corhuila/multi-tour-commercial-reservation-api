package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// Also raised for a departure of another tenant: a 403 would reveal that it exists (AC-07.8).
public class DepartureNotFoundException extends RuntimeException {

    public DepartureNotFoundException(UUID departureId) {
        super("departure " + departureId + " not found");
    }
}
