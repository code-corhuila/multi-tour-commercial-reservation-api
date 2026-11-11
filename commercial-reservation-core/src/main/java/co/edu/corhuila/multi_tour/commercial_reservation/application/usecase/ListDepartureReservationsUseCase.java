package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDepartureReservations;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReservationQueries;
import java.util.UUID;

// The permission comes first. The Administrator holds it by profile; a Collaborator needs the claim (PDR section 9, J5). Manager, Accountant, Analyst and the
// Final Customer never hold it and are refused even with a forged claim. The tenant is the one of the token.
public class ListDepartureReservationsUseCase implements ListDepartureReservations {
    public static final String PERMISSION = "MANAGE_DEPARTURE_EXECUTION";

    private final DepartureReservationQueries queries;

    public ListDepartureReservationsUseCase(DepartureReservationQueries queries) {
        this.queries = queries;
    }

    @Override
    public DepartureReservationPage list(CustomerActor actor, UUID serviceId, UUID salidaId, int page, int limit) {
        boolean allowed = actor != null && actor.tenantId() != null && (actor.role() == Role.TENANT_ADMINISTRATOR
                || (actor.role() == Role.OPERATIONAL_COLLABORATOR && actor.permissions().contains(PERMISSION)));
        if (!allowed) throw new ReservationAccessDeniedException();
        return queries.ofDeparture(actor.tenantId(), serviceId, salidaId, page, limit).orElseThrow(() -> new DepartureNotFoundException(salidaId));
    }
}
