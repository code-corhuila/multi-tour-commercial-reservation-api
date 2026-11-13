package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.InactivateAssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociationStatus;
import java.time.Clock;
import java.util.UUID;

public final class InactivateAssociatedEstablishmentUseCase implements InactivateAssociatedEstablishment {
    private final AssociatedEstablishmentRepository repository;
    private final Clock clock;
    public InactivateAssociatedEstablishmentUseCase(AssociatedEstablishmentRepository repository, Clock clock) {
        this.repository = repository; this.clock = clock;
    }
    @Override public AssociatedEstablishment inactivate(EstablishmentActor actor, UUID associationId, String reason) {
        EstablishmentAuthorization.require(actor, true);
        var item = EstablishmentLookup.require(repository, actor.tenantId(), associationId);
        // The reason is checked even on a repeated call, so a client never learns a bad request was accepted.
        AssociatedEstablishment.requireReason(reason);
        if (item.associationStatus() == AssociationStatus.ACTIVE) {
            item.inactivate(actor.actorId(), reason, clock.instant()); repository.save(item);
        }
        return item;
    }
}
