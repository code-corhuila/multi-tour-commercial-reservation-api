package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PendingPaymentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import java.util.UUID;

// Read side of the pending-payments screen, scoped to the tenant. The effective deadline is derived in the query, never stored.
public interface PendingPaymentQueries {
    PendingPaymentPage page(UUID tenantId, DeadlineKind kind, int page, int limit);
}
