package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetAssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import java.util.UUID;

public final class GetAssociatedEstablishmentUseCase implements GetAssociatedEstablishment {
    private final AssociatedEstablishmentRepository repository;
    public GetAssociatedEstablishmentUseCase(AssociatedEstablishmentRepository repository) { this.repository = repository; }
    @Override public AssociatedEstablishment get(EstablishmentActor actor, UUID associationId) {
        EstablishmentAuthorization.require(actor, false);
        return EstablishmentLookup.require(repository, actor.tenantId(), associationId);
    }
}
