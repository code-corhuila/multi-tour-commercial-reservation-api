package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;

// AC-16.3, RN-02 (RBAC row "Authorize a refund"): only the Tenant Administrator records the decision, authorizes and rejects. The check precedes any load,
// so another role never learns whether a decision exists.
final class RefundAuthorizationRules {

    static final int BUSINESS_DAYS = 5;

    private RefundAuthorizationRules() {}

    // DP-04 (PROPOSED default): the Administrator and the Collaborator read; Manager, Accountant, Analyst and the Final Customer do not.
    static void requireReader(PaymentActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || (actor.role() != Role.TENANT_ADMINISTRATOR && actor.role() != Role.OPERATIONAL_COLLABORATOR))
            throw new PaymentAccessDeniedException();
    }

    static void requireAdministrator(PaymentActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null || actor.role() != Role.TENANT_ADMINISTRATOR)
            throw new PaymentAccessDeniedException();
    }
}
