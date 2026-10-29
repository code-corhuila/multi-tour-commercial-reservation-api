package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor.Role;

public final class CustomerAuthorization {
    // TODO(OPEN): the literal value of the permission inside the claim is not decided (spec "Decisions taken by
    // default 2026-10-28"); the portal uses CONSULT_SENSITIVE_DATA today. This is the only place that names it.
    public static final String SENSITIVE_DATA_PERMISSION = "CONSULT_SENSITIVE_DATA";

    private CustomerAuthorization() {}

    // RN-07: authorized by the token claim "permissions", never by role. Manager, Accountant and Analyst
    // can never hold it, so they are refused even with a forged claim (plan D-07); so is the Final Customer.
    public static void requireSensitiveRead(CustomerActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || (actor.role() != Role.TENANT_ADMINISTRATOR && actor.role() != Role.OPERATIONAL_COLLABORATOR)
                || !actor.permissions().contains(SENSITIVE_DATA_PERMISSION))
            throw new CustomerAccessDeniedException();
    }

    // RBAC matrix (authentication.md): Administrator, Collaborator, Manager and Analyst read the ordinary data;
    // the Accountant and the Final Customer (DP-09) are refused, with or without a claim.
    public static void requireRead(CustomerActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || (actor.role() != Role.TENANT_ADMINISTRATOR && actor.role() != Role.OPERATIONAL_COLLABORATOR
                && actor.role() != Role.MANAGER && actor.role() != Role.ANALYST))
            throw new CustomerAccessDeniedException();
    }

    // RBAC matrix (authentication.md): Administrator and Collaborator write.
    // TODO DP-09: the Final Customer's own routes are not decided, so they are refused here.
    public static void requireWrite(CustomerActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || (actor.role() != Role.TENANT_ADMINISTRATOR && actor.role() != Role.OPERATIONAL_COLLABORATOR))
            throw new CustomerAccessDeniedException();
    }
}
