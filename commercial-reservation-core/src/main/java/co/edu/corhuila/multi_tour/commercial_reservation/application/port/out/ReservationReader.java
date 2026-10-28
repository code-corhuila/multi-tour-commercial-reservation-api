package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import java.util.Optional;
import java.util.UUID;

// Read only: no port of this slice rewrites the fixed general discounts (RN-11).
public interface ReservationReader {
    Optional<ReservationSnapshot> findByTenantAndId(UUID tenantId, UUID reservationId);
}
