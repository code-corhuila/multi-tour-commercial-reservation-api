package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.util.Optional;
import java.util.UUID;

public interface AssociatedEstablishmentRepository {
    // State and pending events must either commit together or leave the stored state untouched.
    void save(AssociatedEstablishment establishment);
    Optional<AssociatedEstablishment> findByTenantAndId(UUID tenantId, UUID associationId);
    // Newest first, with identifier as a stable tie-breaker; empty filters include every value.
    EstablishmentPage listByTenant(UUID tenantId, Optional<AssociationStatus> status,
            Optional<EstablishmentType> type, int page, int limit);
}
