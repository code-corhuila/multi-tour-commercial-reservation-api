package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;

// The general read of a person (plan D-08): ordinary data only. An implementation must not load nor decrypt the
// risk data, so the returned customer always has none; the audited sensitive read goes through CustomerRepository.
public interface CustomerProfileReader {
    Optional<Customer> findOrdinary(UUID tenantId, UUID customerId);
}
