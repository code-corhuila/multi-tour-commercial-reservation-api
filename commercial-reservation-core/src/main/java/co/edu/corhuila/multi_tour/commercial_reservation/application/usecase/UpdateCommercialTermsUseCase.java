package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateCommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTermsChange;
import java.time.Clock;
import java.util.UUID;

public class UpdateCommercialTermsUseCase implements UpdateCommercialTerms {

    private final CatalogServiceRepository repository;
    private final Clock clock;
    private final SalidaCapacityReader capacity;

    public UpdateCommercialTermsUseCase(CatalogServiceRepository repository, Clock clock, SalidaCapacityReader capacity) {
        this.repository = repository;
        this.clock = clock;
        this.capacity = capacity;
    }

    @Override
    public CatalogService update(UUID tenantId, UUID serviceId, CommercialTermsChange change) {
        CatalogService service = CatalogLookup.require(repository, tenantId, serviceId);
        Integer requested = change.configuredCapacity();
        Integer current = service.terms().configuredCapacity();
        // Invalid capacities belong to field validation; only a valid decrease needs held-seat protection.
        if (requested != null && requested > 0 && current != null && requested < current) {
            int held = capacity.maxHeldSeats(tenantId, serviceId);
            if (requested < held) {
                throw new BusinessRuleViolation("Requested capacity " + requested + " is below " + held
                        + " held seats in a scheduled departure");
            }
        }
        service.updateCommercialTerms(change, clock.instant());
        repository.save(service);
        return service;
    }
}
