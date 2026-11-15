package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetOwnReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OwnReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.UUID;

// Another customer's reservation, another tenant's and an unlinked account's all answer like a missing one (AC-08.15, ADR-003).
public class GetOwnReservationUseCase implements GetOwnReservation {

    private final CustomerByAccount accounts;
    private final OwnReservationQueries queries;

    public GetOwnReservationUseCase(CustomerByAccount accounts, OwnReservationQueries queries) {
        this.accounts = accounts;
        this.queries = queries;
    }

    @Override
    public Reservation get(UUID tenantId, UUID accountId, UUID reservationId) {
        var customer = accounts.findByTenantAndAccount(tenantId, accountId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return queries.findByTenantCustomerAndId(tenantId, customer.customerId(), reservationId)
                .filter(found -> found.tenantId().equals(tenantId) && found.customerId().equals(customer.customerId()))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}
