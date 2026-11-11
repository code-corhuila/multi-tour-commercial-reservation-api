package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservationPage;
import java.util.UUID;

// HU-COM-012 amendment 2026-11-10: the reservations of a departure of a tour, for the Administrator or the holder of the permission to execute departures.
public interface ListDepartureReservations {
    DepartureReservationPage list(CustomerActor actor, UUID serviceId, UUID salidaId, int page, int limit);
}
