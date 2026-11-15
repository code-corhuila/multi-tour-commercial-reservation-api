package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.UUID;

public interface GetCustomer {
    // Ordinary data only: the returned customer never carries the risk data.
    Customer get(CustomerActor actor, UUID customerId);
}
