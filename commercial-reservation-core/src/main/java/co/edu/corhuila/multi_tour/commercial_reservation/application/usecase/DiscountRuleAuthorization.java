package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;

public final class DiscountRuleAuthorization {
    private DiscountRuleAuthorization() {}

    // All or nothing (plan D-08). TODO DP-09: reads for Manager, Accountant and Analyst are not decided.
    public static void requireWrite(DiscountRuleActor actor) {
    }
}
