package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import java.util.Set;

// Who reaches the payment routes of HU-COM-009 (AC-09.9, AC-09.17): the Administrator and the Collaborator register and quote, the
// Final Customer does the same on their own reservation (the route checks the ownership), the reading roles follow Consult
// reservations (PROPOSED). The parameters and the proof review have their own rules.
public final class PaymentAuthorization {

    private PaymentAuthorization() {}

    public static void requireRegister(PaymentActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.FINAL_CUSTOMER);
    }

    public static void requireQuote(PaymentActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.FINAL_CUSTOMER);
    }

    public static void requireReadPayments(PaymentActor actor) {
        require(actor, Role.values());
    }

    private static void require(PaymentActor actor, Role... allowed) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null || !Set.of(allowed).contains(actor.role())) {
            throw new PaymentAccessDeniedException();
        }
    }
}
