package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;

// Creates a Pending-payment reservation with its value fixed; it holds no seat (RN-RES-007). Nothing is stored on a refusal.
public interface CreateReservation {

    Reservation create(CreateReservationCommand command);
}
