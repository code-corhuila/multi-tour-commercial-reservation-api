package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicEstablishmentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPublicEstablishments;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.util.Optional;
import java.util.UUID;

// Active associations only; the type is the only filter, there is no search (AC-06.13).
public final class ListPublicEstablishmentsUseCase implements ListPublicEstablishments {

    private final TenantDirectory directory;
    private final AssociatedEstablishmentRepository repository;

    public ListPublicEstablishmentsUseCase(TenantDirectory directory, AssociatedEstablishmentRepository repository) {
        this.directory = directory;
        this.repository = repository;
    }

    @Override
    public PublicEstablishmentPage list(String tenantIdentifier, Optional<EstablishmentType> type, int page, int limit) {
        UUID tenantId = PublicTenants.require(directory, tenantIdentifier);
        PublicTenants.requirePage(page, limit);
        var result = repository.listByTenant(tenantId, Optional.of(AssociationStatus.ACTIVE), type, page, limit);
        var items = result.items().stream()
                .filter(item -> item.tenantId().equals(tenantId) && item.associationStatus() == AssociationStatus.ACTIVE)
                .map(item -> GetPublicEstablishmentUseCase.project(tenantIdentifier, item)).toList();
        return new PublicEstablishmentPage(items, result.total());
    }
}
