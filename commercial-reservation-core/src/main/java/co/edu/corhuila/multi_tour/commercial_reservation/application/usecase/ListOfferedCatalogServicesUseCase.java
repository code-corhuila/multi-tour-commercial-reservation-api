package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListOfferedCatalogServices;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ListOfferedCatalogServicesUseCase implements ListOfferedCatalogServices {

    private final CatalogServiceRepository repository;

    public ListOfferedCatalogServicesUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CatalogService> list(UUID tenantId) {
        return repository.listByTenant(tenantId, Optional.of(CatalogStatus.ACTIVE)).stream()
                .filter(service -> service.tenantId().equals(tenantId)).toList();
    }
}
