package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;

// The reads of a Final Customer (AC-08.7): the customer is a condition of the query, never a filter applied afterwards (plan D-07).
public interface OwnReservationQueries {

    ReservationPage page(UUID tenantId, UUID customerId, boolean includeArchived, int page, int limit);

    Optional<Reservation> findByTenantCustomerAndId(UUID tenantId, UUID customerId, UUID reservationId);
}
