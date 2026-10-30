package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;

// The reads of HU-COM-007 (AC-07.16, AC-07.17); both are tenant scoped and never recalculate a value (AC-07.10).
public interface ReservationQueries {

    // Newest first. The archived states (COMPLETED, CANCELLED) are left out unless includeArchived is set (DP-10).
    ReservationPage page(UUID tenantId, boolean includeArchived, int page, int limit);

    Optional<Reservation> findByTenantAndId(UUID tenantId, UUID reservationId);
}
