package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.UUID;

public interface GetOwnReservation {

    Reservation get(UUID tenantId, UUID accountId, UUID reservationId);
}
