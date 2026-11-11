package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListOwnReservations;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OwnReservationQueries;
import java.util.List;
import java.util.UUID;

// An account with no customer record has no reservations: the list is empty, not an error (AC-08.7).
public class ListOwnReservationsUseCase implements ListOwnReservations {

    private final CustomerByAccount accounts;
    private final OwnReservationQueries queries;

    public ListOwnReservationsUseCase(CustomerByAccount accounts, OwnReservationQueries queries) {
        this.accounts = accounts;
        this.queries = queries;
    }

    @Override
    public ReservationPage list(UUID tenantId, UUID accountId, boolean includeArchived, int page, int limit) {
        return accounts.findByTenantAndAccount(tenantId, accountId)
                .map(customer -> queries.page(tenantId, customer.customerId(), includeArchived, page, limit))
                .orElseGet(() -> new ReservationPage(List.of(), 0));
    }
}
