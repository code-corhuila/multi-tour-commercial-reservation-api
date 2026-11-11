package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadCustomerName;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.UUID;

public class ReadCustomerNameUseCase implements ReadCustomerName {

    private final CustomerRepository customers;

    public ReadCustomerNameUseCase(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public String nameOf(UUID tenantId, UUID customerId) {
        return customers.findByTenantAndId(tenantId, customerId).map(Customer::fullName).orElse("");
    }
}
