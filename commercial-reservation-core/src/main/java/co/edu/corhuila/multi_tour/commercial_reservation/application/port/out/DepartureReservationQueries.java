package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservationPage;
import java.util.Optional;
import java.util.UUID;

// Empty when the departure does not exist for the tenant and the tour; a departure without reservations is a page with no rows.
public interface DepartureReservationQueries {
    Optional<DepartureReservationPage> ofDeparture(UUID tenantId, UUID serviceId, UUID salidaId, int page, int limit);
}
