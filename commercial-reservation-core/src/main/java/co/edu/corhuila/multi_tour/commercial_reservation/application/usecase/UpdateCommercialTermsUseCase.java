package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateCommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTermsChange;
import java.time.Clock;
import java.util.UUID;

public class UpdateCommercialTermsUseCase implements UpdateCommercialTerms {

    private final CatalogServiceRepository repository;
    private final Clock clock;

    public UpdateCommercialTermsUseCase(CatalogServiceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public CatalogService update(UUID tenantId, UUID serviceId, CommercialTermsChange change) {
        CatalogService service = CatalogLookup.require(repository, tenantId, serviceId);
        service.updateCommercialTerms(change, clock.instant());
        repository.save(service);
        return service;
    }
}
