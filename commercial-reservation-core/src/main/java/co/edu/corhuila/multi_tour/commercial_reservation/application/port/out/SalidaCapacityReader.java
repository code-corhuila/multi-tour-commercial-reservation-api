package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

public interface SalidaCapacityReader {
    /** Returns the largest held-seat count for this tenant and service, or zero without departures. */
    int maxHeldSeats(UUID tenantId, UUID serviceId);
}
