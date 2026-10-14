package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.util.UUID;

public class GetCatalogServiceUseCase implements GetCatalogService {

    private final CatalogServiceRepository repository;

    public GetCatalogServiceUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Override
    public CatalogService get(UUID tenantId, UUID serviceId) {
        return CatalogLookup.require(repository, tenantId, serviceId);
    }
}
