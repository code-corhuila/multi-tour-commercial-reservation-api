package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// What the start and the end of a departure need to know: its status in the mirror (empty when it does not exist for the tenant) and the reservations that
// hold it. Both are scoped by the tenant. Nothing personal.
public interface DepartureExecutionReader {
    Optional<DepartureStatus> statusOf(UUID tenantId, UUID salidaId);

    List<UUID> reservationIds(UUID tenantId, UUID salidaId);
}
