package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import java.util.UUID;

public interface ListOwnReservations {

    ReservationPage list(UUID tenantId, UUID accountId, boolean includeArchived, int page, int limit);
}
