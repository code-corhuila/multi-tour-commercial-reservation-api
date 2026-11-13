package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicy;
import java.util.List;
import java.util.UUID;

// HU-COM-013 DP-02a: the rows of cancellation_penalty_policy of one tenant. A save replaces the row of the service (or of the tenant), keeping the last change only.
public interface CancellationPolicyRepository {

    List<CancellationPolicy> findByTenant(UUID tenantId);

    boolean serviceExists(UUID tenantId, UUID serviceId);

    void save(UUID tenantId, CancellationPolicy policy);
}
