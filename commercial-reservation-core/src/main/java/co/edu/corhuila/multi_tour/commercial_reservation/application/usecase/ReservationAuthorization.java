package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor.Role;
import java.util.Set;

// RN-10 (AC-07.13): Administrator and Collaborator quote and create; Manager, Accountant and Analyst read; the departures also
// go to the Final Customer.
// HU-COM-008: the Final Customer also quotes, creates and reads through the same routes; what they read is their own (own scope).
public final class ReservationAuthorization {

    private ReservationAuthorization() {}

    public static void requireWrite(CustomerActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.FINAL_CUSTOMER);
    }

    public static void requireRead(CustomerActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER);
    }

    public static void requireDepartures(CustomerActor actor) {
        require(actor, Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ANALYST, Role.FINAL_CUSTOMER);
    }

    private static void require(CustomerActor actor, Role... allowed) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null || !Set.of(allowed).contains(actor.role())) {
            throw new ReservationAccessDeniedException();
        }
    }
}
