package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.InternalDepartureReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListInternalDepartureReservations;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InternalDepartureReservationQueries;
import java.util.UUID;

// The route admits the SERVICE role alone, so there is no permission to decide here: the departure of the tenant either exists or it is not found.
public class ListInternalDepartureReservationsUseCase implements ListInternalDepartureReservations {
    private final InternalDepartureReservationQueries queries;

    public ListInternalDepartureReservationsUseCase(InternalDepartureReservationQueries queries) {
        this.queries = queries;
    }

    @Override
    public InternalDepartureReservationPage list(UUID tenantId, UUID salidaId, int page, int limit) {
        return queries.ofDeparture(tenantId, salidaId, page, limit).orElseThrow(() -> new DepartureNotFoundException(salidaId));
    }
}
