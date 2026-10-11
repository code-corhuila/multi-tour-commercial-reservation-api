package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListAssociatedEstablishments;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.util.Optional;

public final class ListAssociatedEstablishmentsUseCase implements ListAssociatedEstablishments {
    private final AssociatedEstablishmentRepository repository;
    public ListAssociatedEstablishmentsUseCase(AssociatedEstablishmentRepository repository) { this.repository = repository; }

    @Override public EstablishmentPage list(EstablishmentActor actor, Optional<AssociationStatus> status,
            Optional<EstablishmentType> type, int page, int limit) {
        EstablishmentAuthorization.require(actor, false);
        if (page < 1) throw new DomainException("page", "page must be at least 1");
        if (limit < 1 || limit > 100) throw new DomainException("limit", "limit must be between 1 and 100");
        var result = repository.listByTenant(actor.tenantId(), status, type, page, limit);
        // Reject a broken page rather than exposing foreign items or their pagination metadata.
        if (result.items().stream().anyMatch(item -> !item.tenantId().equals(actor.tenantId())))
            throw new AssociatedEstablishmentNotFoundException();
        return result;
    }
}
