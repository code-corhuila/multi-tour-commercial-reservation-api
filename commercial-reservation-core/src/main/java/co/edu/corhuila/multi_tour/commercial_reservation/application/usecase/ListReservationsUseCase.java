package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservations;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationQueries;
import java.util.UUID;

public class ListReservationsUseCase implements ListReservations {

    private final ReservationQueries queries;

    public ListReservationsUseCase(ReservationQueries queries) {
        this.queries = queries;
    }

    @Override
    public ReservationPage list(UUID tenantId, boolean includeArchived, int page, int limit) {
        return queries.page(tenantId, includeArchived, page, limit);
    }
}
