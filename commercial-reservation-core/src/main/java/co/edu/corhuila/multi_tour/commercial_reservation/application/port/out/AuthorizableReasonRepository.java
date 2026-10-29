package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// save inserts or updates the enabled flag; there is no deletion on purpose.
public interface AuthorizableReasonRepository {
    void save(AuthorizableReason reason);

    Optional<AuthorizableReason> findByTenantAndId(UUID tenantId, UUID reasonId);

    List<AuthorizableReason> findByTenant(UUID tenantId);
}
