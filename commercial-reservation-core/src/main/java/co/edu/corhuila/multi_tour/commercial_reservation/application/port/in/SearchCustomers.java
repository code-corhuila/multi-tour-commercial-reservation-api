package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerSearchPage;

public interface SearchCustomers {
    // Ordinary data only, of the tenant of the actor; limit and cursor are optional.
    CustomerSearchPage search(CustomerActor actor, String text, Integer limit, String cursor);
}
