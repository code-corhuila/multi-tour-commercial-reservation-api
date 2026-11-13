package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentChangePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListEstablishmentChanges;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EstablishmentChangeLog;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.util.Optional;

public final class ListEstablishmentChangesUseCase implements ListEstablishmentChanges {
    private final EstablishmentChangeLog log;
    public ListEstablishmentChangesUseCase(EstablishmentChangeLog log) { this.log = log; }

    @Override public EstablishmentChangePage list(EstablishmentActor actor, Optional<EstablishmentType> type, int page, int limit) {
        // Same readers as the list of establishments: whoever may read them may read their trace.
        EstablishmentAuthorization.require(actor, false);
        if (page < 1) throw new DomainException("page", "page must be at least 1");
        if (limit < 1 || limit > 100) throw new DomainException("limit", "limit must be between 1 and 100");
        var result = log.listByTenant(actor.tenantId(), type, page, limit);
        // Reject a broken page rather than exposing another tenant's entries.
        if (result.items().stream().anyMatch(change -> !change.tenantId().equals(actor.tenantId())))
            throw new AssociatedEstablishmentNotFoundException();
        return result;
    }
}
