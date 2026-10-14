package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.InactivateCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.time.Clock;
import java.util.UUID;

public class InactivateCatalogServiceUseCase implements InactivateCatalogService {

    private final CatalogServiceRepository repository;
    private final Clock clock;

    public InactivateCatalogServiceUseCase(CatalogServiceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public CatalogService inactivate(UUID tenantId, UUID serviceId, UUID actorId) {
        CatalogService service = CatalogLookup.require(repository, tenantId, serviceId);
        service.inactivate(actorId, clock.instant());
        repository.save(service);
        return service;
    }
}
