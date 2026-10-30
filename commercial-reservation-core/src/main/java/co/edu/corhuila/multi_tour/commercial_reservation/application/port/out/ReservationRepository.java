package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;

// No delete operation on purpose: a reservation is never deleted, it changes state. The reads join with T-09 and T-10.
public interface ReservationRepository {

    void save(Reservation reservation);
}
