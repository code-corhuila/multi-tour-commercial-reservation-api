package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import java.util.UUID;

// HU-COM-012 AC-12.9: one stored change of a reservation, for the replay of a repeated request. The staff and the reading roles.
public interface GetReservationModification {
    ReservationModificationRecord get(PaymentActor actor, UUID reservationId, UUID modificationId);
}
