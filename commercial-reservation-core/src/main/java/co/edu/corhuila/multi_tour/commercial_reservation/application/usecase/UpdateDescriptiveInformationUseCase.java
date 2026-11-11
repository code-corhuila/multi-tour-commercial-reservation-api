package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateDescriptiveInformation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.time.Clock;
import java.util.UUID;

public class UpdateDescriptiveInformationUseCase implements UpdateDescriptiveInformation {

    private final CatalogServiceRepository repository;
    private final Clock clock;

    public UpdateDescriptiveInformationUseCase(CatalogServiceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public CatalogService update(UUID tenantId, UUID serviceId, String commercialName) {
        CatalogService service = CatalogLookup.require(repository, tenantId, serviceId);
        service.updateDescriptiveInformation(commercialName, clock.instant());
        repository.save(service);
        return service;
    }
}
