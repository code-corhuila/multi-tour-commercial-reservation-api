package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.InternalDepartureReservationPage;
import java.util.Optional;
import java.util.UUID;

// Empty when the departure does not exist for the tenant; a departure without reservations is a page with no rows.
public interface InternalDepartureReservationQueries {
    Optional<InternalDepartureReservationPage> ofDeparture(UUID tenantId, UUID salidaId, int page, int limit);
}
