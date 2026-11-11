package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.UUID;

public class GetReservationUseCase implements GetReservation {

    private final ReservationQueries queries;

    public GetReservationUseCase(ReservationQueries queries) {
        this.queries = queries;
    }

    @Override
    public Reservation get(UUID tenantId, UUID reservationId) {
        // Re-checked so isolation does not depend on every adapter remembering its tenant predicate (RN-09).
        return queries.findByTenantAndId(tenantId, reservationId).filter(found -> found.tenantId().equals(tenantId))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}
