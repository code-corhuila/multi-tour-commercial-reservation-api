package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.time.Clock;

public class RegisterCatalogServiceUseCase implements RegisterCatalogService {

    private final CatalogServiceRepository repository;
    private final IdGenerator ids;
    private final Clock clock;

    public RegisterCatalogServiceUseCase(CatalogServiceRepository repository, IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public CatalogService register(Command command) {
        CommercialTerms terms = CommercialTerms.of(command.baseRateCents(), command.baseOperationalCostCents(),
                command.configuredCapacity(), command.transportRequired(), command.highRisk());
        CatalogService service = CatalogService.register(ids.newId(), command.tenantId(), command.serviceType(),
                command.commercialName(), terms, clock.instant());
        repository.save(service);
        return service;
    }
}
