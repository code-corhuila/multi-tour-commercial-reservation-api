package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;

// RN-07 (plan D-06): the Tenant Administrator holds the permission by profile, an Operational Collaborator by the token claim;
// Manager, Accountant, Analyst and the Final Customer never hold it and are refused even with a forged claim.
public final class PaymentProofAuthorization {
    // DP-10 (default): the literal of the permission is not published yet; this is the only place that names it.
    public static final String VALIDATE_PROOFS_PERMISSION = "VALIDATE_PAYMENT_PROOFS";

    private PaymentProofAuthorization() {}

    public static void requireDecide(PaymentActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null) throw new PaymentAccessDeniedException();
        boolean allowed = actor.role() == Role.TENANT_ADMINISTRATOR
                || (actor.role() == Role.OPERATIONAL_COLLABORATOR && actor.permissions().contains(VALIDATE_PROOFS_PERMISSION));
        if (!allowed) throw new PaymentAccessDeniedException();
    }
}
