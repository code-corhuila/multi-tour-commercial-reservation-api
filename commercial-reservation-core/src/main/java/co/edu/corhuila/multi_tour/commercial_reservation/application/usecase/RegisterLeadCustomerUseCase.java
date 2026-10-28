package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRegisteredOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ServiceRiskReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.time.Clock;

public class RegisterLeadCustomerUseCase implements RegisterLeadCustomer {

    public RegisterLeadCustomerUseCase(CustomerRepository repository, CustomerRegisteredOutbox outbox,
            CustomerTransaction transaction, ServiceRiskReader risk, IdGenerator ids, Clock clock) {
    }

    @Override
    public Customer register(CustomerActor actor, Command command) {
        return null;
    }
}
