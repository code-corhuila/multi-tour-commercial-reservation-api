package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;

// RN-07 (plan D-06): the Tenant Administrator holds the permission by profile, an Operational Collaborator by the token claim;
// Manager, Accountant, Analyst and the Final Customer never hold it and are refused even with a forged claim.
public final class PaymentProofAuthorization {
    // DP-10: the literal of the permission is published in the security service; this is the only place in this service that names it.
    public static final String VALIDATE_PROOFS_PERMISSION = "VALIDATE_PAYMENT_PROOFS";

    private PaymentProofAuthorization() {}

    public static void requireDecide(PaymentActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null) throw new PaymentAccessDeniedException();
        boolean allowed = actor.role() == Role.TENANT_ADMINISTRATOR
                || (actor.role() == Role.OPERATIONAL_COLLABORATOR && actor.permissions().contains(VALIDATE_PROOFS_PERMISSION));
        if (!allowed) throw new PaymentAccessDeniedException();
    }

    // HU-COM-010 DP-02 (decided by the Project Lead, 2026-11-12): only the Administrator and the Operational Collaborator read the proofs, their list, detail and history; Manager, Accountant,
    // Analyst and the Final Customer do not (the customer reads through the history of its own reservation only, which does not call this check). The read-only Platform Support actor keeps
    // the read it holds by HU-SEC-007 (Project Lead, 2026-11-11); the 2026-11-12 decision does not name it, which is a question for the Project Lead.
    public static void requireRead(PaymentActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null) throw new PaymentAccessDeniedException();
        boolean allowed = actor.role() == Role.TENANT_ADMINISTRATOR || actor.role() == Role.OPERATIONAL_COLLABORATOR || actor.role() == Role.PLATFORM_SUPPORT;
        if (!allowed) throw new PaymentAccessDeniedException();
    }

    public static boolean isOwner(PaymentActor actor, Reservation reservation) {
        return actor != null && actor.role() == Role.FINAL_CUSTOMER && actor.actorId() != null
                && actor.actorId().equals(reservation.customerId());
    }
}
