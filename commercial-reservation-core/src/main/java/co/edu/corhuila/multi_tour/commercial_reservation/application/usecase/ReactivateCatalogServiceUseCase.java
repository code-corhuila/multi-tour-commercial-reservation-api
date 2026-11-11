package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReactivateCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.time.Clock;
import java.util.UUID;

public class ReactivateCatalogServiceUseCase implements ReactivateCatalogService {

    private final CatalogServiceRepository repository;
    private final Clock clock;

    public ReactivateCatalogServiceUseCase(CatalogServiceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public CatalogService reactivate(UUID tenantId, UUID serviceId, UUID actorId) {
        CatalogService service = CatalogLookup.require(repository, tenantId, serviceId);
        service.reactivate(actorId, clock.instant());
        repository.save(service);
        return service;
    }
}
