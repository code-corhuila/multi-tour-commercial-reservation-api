package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import java.util.UUID;

// HU-COM-012 AC-12.6: the history of the changes of a reservation, newest first. The staff and the reading roles; never the customer (DP-08).
public interface ListReservationModifications {
    ModificationPage list(PaymentActor actor, UUID reservationId, int page, int limit);
}
