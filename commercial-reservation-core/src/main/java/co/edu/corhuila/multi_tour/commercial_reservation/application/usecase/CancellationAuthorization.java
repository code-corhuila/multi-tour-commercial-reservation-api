package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import java.util.Set;

// HU-COM-013 RN-06, AC-13.5: only the Administrator and the Collaborator quote and cancel; never the Final Customer, whatever the matrix
// cell says (RN-M-13). The record follows Consult reservations (DP-03). The check runs before any load.
public final class CancellationAuthorization {

    private CancellationAuthorization() {}

    public static void requireCancel(PaymentActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR);
    }

    public static void requireRead(PaymentActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.PLATFORM_SUPPORT);
    }

    // HU-COM-013 DP-02d: the penalty policy is the Administrator's alone, decided on the server.
    public static void requireChangePolicy(PaymentActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR);
    }

    private static void require(PaymentActor actor, Role... allowed) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null || !Set.of(allowed).contains(actor.role())) {
            throw new PaymentAccessDeniedException();
        }
    }
}
