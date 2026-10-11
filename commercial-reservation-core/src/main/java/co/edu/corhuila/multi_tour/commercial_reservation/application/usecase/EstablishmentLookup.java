package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import java.util.UUID;

final class EstablishmentLookup {
    private EstablishmentLookup() {}

    static AssociatedEstablishment require(AssociatedEstablishmentRepository repository, UUID tenantId, UUID associationId) {
        // Tenant isolation cannot depend on every repository implementation remembering its predicate.
        return repository.findByTenantAndId(tenantId, associationId)
                .filter(item -> item.tenantId().equals(tenantId))
                .orElseThrow(AssociatedEstablishmentNotFoundException::new);
    }
}
