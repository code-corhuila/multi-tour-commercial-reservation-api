package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTourPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPublicTours;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

// Active tours only, newest first, one quote per tour (plan D-05, D-06; 1+N until measured in T-03).
public final class ListPublicToursUseCase implements ListPublicTours {

    private final TenantDirectory directory;
    private final CatalogServiceRepository repository;
    private final PublicTourProjection projection;

    public ListPublicToursUseCase(TenantDirectory directory, CatalogServiceRepository repository,
            QuoteDiscountBreakdown quote, Clock clock) {
        this.directory = directory;
        this.repository = repository;
        this.projection = new PublicTourProjection(quote, clock);
    }

    @Override
    public PublicTourPage list(String tenantIdentifier, Optional<String> search, int page, int limit) {
        UUID tenantId = PublicTenants.require(directory, tenantIdentifier);
        PublicTenants.requirePage(page, limit);
        Optional<String> term = search.map(String::trim).filter(text -> !text.isEmpty());
        var result = repository.pageByTenant(tenantId, Optional.of(CatalogStatus.ACTIVE),
                Optional.of(CatalogServiceType.TOUR), term, page, limit);
        var tours = result.items().stream().filter(item -> PublicTourProjection.isOffered(item, tenantId))
                .map(projection::project).toList();
        return new PublicTourPage(tours, result.total());
    }
}
