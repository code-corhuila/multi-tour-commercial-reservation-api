package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerProfileReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.UUID;

public class GetCustomerUseCase implements GetCustomer {
    private final CustomerProfileReader reader;

    public GetCustomerUseCase(CustomerProfileReader reader) {
        this.reader = reader;
    }

    @Override
    public Customer get(CustomerActor actor, UUID customerId) {
        CustomerAuthorization.requireRead(actor);
        // The tenant is the actor's: a customer of another tenant looks missing (RN-12).
        return reader.findOrdinary(actor.tenantId(), customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
