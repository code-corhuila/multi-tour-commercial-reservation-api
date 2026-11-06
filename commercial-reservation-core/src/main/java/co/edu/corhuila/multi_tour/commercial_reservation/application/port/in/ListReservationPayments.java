package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPayments;
import java.util.UUID;

// The history of the payments of a reservation, newest first. A Final Customer reads only an own reservation.
public interface ListReservationPayments {
    ReservationPayments list(PaymentActor actor, UUID reservationId, int page, int limit);
}
