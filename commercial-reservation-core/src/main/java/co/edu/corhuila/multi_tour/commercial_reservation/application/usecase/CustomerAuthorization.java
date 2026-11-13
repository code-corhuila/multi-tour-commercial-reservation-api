package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor.Role;

public final class CustomerAuthorization {
    // Decision (default, spec "Decisions taken by default 2026-10-28", pending the Project Lead): the permission inside the claim is
    // CONSULT_SENSITIVE_DATA, as the portal sends it today. This is the only place that names it.
    public static final String SENSITIVE_DATA_PERMISSION = "CONSULT_SENSITIVE_DATA";

    private CustomerAuthorization() {}

    // RN-07: authorized by the token claim "permissions", never by role. Manager, Accountant and Analyst
    // can never hold it, so they are refused even with a forged claim (plan D-07); so is the Final Customer.
    public static void requireSensitiveRead(CustomerActor actor) {
        // HU-SEC-007: the read only support actor has no permission claim; the scope SENSITIVE of its token is checked by the gate of the service.
        if (actor != null && actor.role() == Role.PLATFORM_SUPPORT && actor.tenantId() != null && actor.actorId() != null) return;
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
                && actor.role() != Role.MANAGER && actor.role() != Role.ANALYST && actor.role() != Role.PLATFORM_SUPPORT))
            throw new CustomerAccessDeniedException();
    }

    // RBAC matrix (authentication.md): Administrator and Collaborator write.
    // Decision (spec 005 DP-09, open, owned by HU-COM-014): the own-data routes of the Final Customer are not defined, so they are refused here.
    public static void requireWrite(CustomerActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || (actor.role() != Role.TENANT_ADMINISTRATOR && actor.role() != Role.OPERATIONAL_COLLABORATOR))
            throw new CustomerAccessDeniedException();
    }
}
