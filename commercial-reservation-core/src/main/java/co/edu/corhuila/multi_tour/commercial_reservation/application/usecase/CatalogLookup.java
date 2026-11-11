package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.util.UUID;

final class CatalogLookup {

    private CatalogLookup() {
    }

    static CatalogService require(CatalogServiceRepository repository, UUID tenantId, UUID serviceId) {
        // Re-checked here so isolation does not depend on every adapter remembering its tenant predicate (RN-09).
        return repository.findByTenantAndId(tenantId, serviceId)
                .filter(service -> service.tenantId().equals(tenantId))
                .orElseThrow(() -> new CatalogServiceNotFoundException(serviceId));
    }
}
