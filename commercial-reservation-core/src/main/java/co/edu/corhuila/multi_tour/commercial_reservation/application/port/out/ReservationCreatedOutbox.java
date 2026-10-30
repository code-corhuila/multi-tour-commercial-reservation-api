package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationCreated;

// Joins the transaction of the caller: the event commits or rolls back with the reservation (norm 5.3.11).
public interface ReservationCreatedOutbox {
    void append(ReservationCreated event);
}
