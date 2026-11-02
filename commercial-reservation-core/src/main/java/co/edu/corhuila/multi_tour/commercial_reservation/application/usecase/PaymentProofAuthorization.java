package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;

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

    // DP-02 (default): Administrator and Collaborator read the proofs (CONFIRMADO, RF-015A); Manager, Accountant and Analyst
    // follow Consult reservations (PROPOSED). The Final Customer reads through the history of its own reservation only.
    public static void requireRead(PaymentActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null || actor.role() == Role.FINAL_CUSTOMER)
            throw new PaymentAccessDeniedException();
    }

    // AC-10.19: the file is for whoever may decide, or for the customer who owns the reservation (the token subject is the
    // customer, an open point with the customer account, G-USR); the other roles read the data and never the file.
    public static void requireFile(PaymentActor actor, Reservation reservation) {
        if (isOwner(actor, reservation)) return;
        requireDecide(actor);
    }

    public static boolean isOwner(PaymentActor actor, Reservation reservation) {
        return actor != null && actor.role() == Role.FINAL_CUSTOMER && actor.actorId() != null
                && actor.actorId().equals(reservation.customerId());
    }
}
