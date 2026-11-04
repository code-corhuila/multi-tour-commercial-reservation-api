package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueDeadlinePage;
import java.util.UUID;

// What job 1 asks to expire (AC-11.2): the deadlines of the tenant that were reached, not frozen and not obviously paid. It decides
// nothing; the expiry re-checks every one. The tenant is the path of the internal route, as a service token has none.
public interface ListDuePaymentDeadlines {
    DueDeadlinePage list(UUID tenantId, int page, int limit);
}
