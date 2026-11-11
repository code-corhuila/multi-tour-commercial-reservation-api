package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueDeadlinePage;
import java.time.Instant;
import java.util.UUID;

// The deadlines of one tenant whose effective instant is at or before now, oldest first, unfrozen, of a reservation that can still expire.
public interface DuePaymentQueries {
    DueDeadlinePage page(UUID tenantId, Instant now, int page, int limit);
}
