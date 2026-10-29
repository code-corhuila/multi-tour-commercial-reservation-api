package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;

// No deletion on purpose: physical deletion of a person is out of scope (spec section 6).
public interface CustomerRepository {
    void save(Customer customer);

    Optional<Customer> findByTenantAndId(UUID tenantId, UUID customerId);
}
