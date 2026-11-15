package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundBasis;
import java.util.Optional;
import java.util.UUID;

// Read only; the tenant leads the query, so a reservation of another tenant is the same empty answer as a missing one.
public interface RefundBasisReader {
    Optional<RefundBasis> find(UUID tenantId, UUID reservationId);
}
