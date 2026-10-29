package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CustomerRegistered;

public interface CustomerRegisteredOutbox {
    void append(CustomerRegistered event);
}
