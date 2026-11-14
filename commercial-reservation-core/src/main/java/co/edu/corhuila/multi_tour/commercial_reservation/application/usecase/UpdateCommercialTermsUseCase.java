package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateCommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ServiceDepartures;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTermsChange;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import java.time.Clock;
import java.util.UUID;

public class UpdateCommercialTermsUseCase implements UpdateCommercialTerms {

    private final CatalogServiceRepository repository;
    private final Clock clock;
    private final SalidaCapacityReader capacity;
    private final ServiceDepartures departures;

    // Without the departures port nothing is recomputed: the catalog alone, as before ND-08.
    public UpdateCommercialTermsUseCase(CatalogServiceRepository repository, Clock clock, SalidaCapacityReader capacity) {
        this(repository, clock, capacity, null);
    }

    public UpdateCommercialTermsUseCase(CatalogServiceRepository repository, Clock clock, SalidaCapacityReader capacity,
            ServiceDepartures departures) {
        this.repository = repository;
        this.clock = clock;
        this.capacity = capacity;
        this.departures = departures;
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
        realign(tenantId, serviceId, requested, service.terms().transportRequired());
        return service;
    }

    // RN-SAL-001 and H8 (ND-08, DP-D6-8), in the transaction of the catalog update: the effective capacity of every open departure of the
    // service follows the new tour capacity, from the vehicle total it stored (or from the tour alone when transport is not mandatory and
    // no total is known). It never falls below the held seats; with mandatory transport and no total it is left as it is (G-VEH).
    private void realign(UUID tenantId, UUID serviceId, Integer requested, boolean transportRequired) {
        if (departures == null || requested == null || requested <= 0) {
            return;
        }
        for (DepartureState row : departures.lockAllOf(tenantId, serviceId)) {
            DepartureState next = row.withTourCapacity(requested, transportRequired);
            if (next.effectiveCapacity() != row.effectiveCapacity()) {
                departures.save(next);
            }
        }
    }
}
