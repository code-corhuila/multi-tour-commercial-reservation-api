package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import java.util.UUID;

public interface ListReservations {

    ReservationPage list(UUID tenantId, boolean includeArchived, int page, int limit);
}
