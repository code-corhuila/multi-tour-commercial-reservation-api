package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Reads the capacity rows of a tour, future and scheduled ones, ordered by start (plan D-07). It keeps the full
// ones: the public use case drops what has no seats and the authenticated list of HU-COM-007 keeps them.
public interface DepartureReader {

    List<DepartureState> findOpen(UUID tenantId, UUID serviceId, Instant now);

    // One row by id for the creation check of HU-COM-007 (plan D-05); empty for another tenant. Read only. Until the JDBC
    // adapter overrides it the check is refused instead of silently passing.
    default Optional<DepartureState> find(UUID tenantId, UUID departureId) {
        throw new UnsupportedOperationException("the departure lookup needs the adapter override");
    }
}
