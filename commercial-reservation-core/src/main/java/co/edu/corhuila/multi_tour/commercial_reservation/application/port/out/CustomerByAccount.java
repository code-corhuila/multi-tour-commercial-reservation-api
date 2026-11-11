package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;

// The customer record linked to an account of the tenant: at most one (HU-COM-008, plan D-02; the -db enforces it).
public interface CustomerByAccount {
    Optional<Customer> findByTenantAndAccount(UUID tenantId, UUID accountId);
}
