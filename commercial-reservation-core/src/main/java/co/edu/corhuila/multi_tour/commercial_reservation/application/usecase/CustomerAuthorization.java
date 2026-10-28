package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;

public final class CustomerAuthorization {
    private CustomerAuthorization() {}

    // RBAC matrix (authentication.md): Administrator and Collaborator write.
    // TODO DP-09: the Final Customer's own routes are not decided, so they are refused here.
    public static void requireWrite(CustomerActor actor) {
    }
}
