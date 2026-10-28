package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;

public final class DiscountRuleAuthorization {
    private DiscountRuleAuthorization() {}

    // All or nothing (plan D-08): only the Tenant Administrator writes. TODO DP-09: reads for Manager,
    // Accountant and Analyst are not decided, so no read policy exists here.
    public static void requireWrite(DiscountRuleActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.role() != DiscountRuleActor.Role.TENANT_ADMINISTRATOR)
            throw new DiscountRuleAccessDeniedException();
    }
}
