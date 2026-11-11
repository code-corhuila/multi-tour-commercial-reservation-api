package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import java.util.Set;

// Who reaches the payment routes of HU-COM-009 (AC-09.9, AC-09.17): the Administrator and the Collaborator register and quote, the
// Final Customer does the same on their own reservation (the route checks the ownership), the reading roles follow Consult
// reservations (PROPOSED) and only the Administrator changes the parameters. The proof review has its own class.
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

    // HU-COM-011 (RF-015): the Administrator and the Collaborator follow up and extend; the reading roles only read.
    public static void requireExtend(PaymentActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR);
    }
    // HU-COM-011 (RF-015): the Administrator and the Collaborator record the follow-up; DP-02 lets the reading roles see deadlines and history.
    public static void requireFollowUp(PaymentActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR);
    }

    public static void requireReadDeadlines(PaymentActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.PLATFORM_SUPPORT);
    }

    // PDR 1.9, AC-RS.4: the Final Customer reads the enabled types and methods too, but only the reduced projection (readOptions).
    public static void requireReadParameters(PaymentActor actor) {
        require(actor, Role.values());
    }

    public static void requireChangeParameters(PaymentActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR);
    }

    private static void require(PaymentActor actor, Role... allowed) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null || !Set.of(allowed).contains(actor.role())) {
            throw new PaymentAccessDeniedException();
        }
    }
}
