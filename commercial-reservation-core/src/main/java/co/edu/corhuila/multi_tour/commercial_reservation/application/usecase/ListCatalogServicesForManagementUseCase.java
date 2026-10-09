package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListCatalogServicesForManagement;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import java.util.Optional;
import java.util.UUID;

public class ListCatalogServicesForManagementUseCase implements ListCatalogServicesForManagement {

    private final CatalogServiceRepository repository;

    public ListCatalogServicesForManagementUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Override
    public CatalogPage list(UUID tenantId, Optional<CatalogStatus> status, Optional<CatalogServiceType> type,
            int page, int limit) {
        var result = repository.pageByTenant(tenantId, status, type, page, limit);
        return new CatalogPage(result.items().stream()
                .filter(service -> service.tenantId().equals(tenantId)).toList(), result.total());
    }
}
