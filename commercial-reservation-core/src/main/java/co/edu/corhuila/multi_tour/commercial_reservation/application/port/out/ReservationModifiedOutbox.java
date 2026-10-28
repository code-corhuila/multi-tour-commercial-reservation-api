package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationModified;

public interface ReservationModifiedOutbox {
    void append(ReservationModified event);
}
