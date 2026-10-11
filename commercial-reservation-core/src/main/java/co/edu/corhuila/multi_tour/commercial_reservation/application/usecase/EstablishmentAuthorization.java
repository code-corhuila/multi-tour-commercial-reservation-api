package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor.Role.*;

public final class EstablishmentAuthorization {
    private EstablishmentAuthorization() {}

    // RF-018 grants whole operations, so one policy prevents field-level exceptions from appearing.
    public static void require(EstablishmentActor actor, boolean write) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null || actor.role() == null)
            throw new EstablishmentAccessDeniedException();
        if (actor.role() == TENANT_ADMINISTRATOR) return;
        if (!write && (actor.role() == MANAGER || actor.role() == ANALYST)) return;
        throw new EstablishmentAccessDeniedException();
    }
}
