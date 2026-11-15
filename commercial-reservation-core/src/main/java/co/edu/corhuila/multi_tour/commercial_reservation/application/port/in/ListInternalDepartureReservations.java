package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.InternalDepartureReservationPage;
import java.util.UUID;

// G-API6-1: the reservations of a departure for a service (operations-cost). The tenant is the one of the path of the route.
public interface ListInternalDepartureReservations {
    InternalDepartureReservationPage list(UUID tenantId, UUID salidaId, int page, int limit);
}
