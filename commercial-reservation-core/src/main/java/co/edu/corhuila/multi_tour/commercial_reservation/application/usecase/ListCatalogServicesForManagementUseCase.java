package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListCatalogServicesForManagement;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ListCatalogServicesForManagementUseCase implements ListCatalogServicesForManagement {

    private final CatalogServiceRepository repository;

    public ListCatalogServicesForManagementUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CatalogService> list(UUID tenantId, Optional<CatalogStatus> status) {
        return repository.listByTenant(tenantId, status).stream()
                .filter(service -> service.tenantId().equals(tenantId)).toList();
    }
}
