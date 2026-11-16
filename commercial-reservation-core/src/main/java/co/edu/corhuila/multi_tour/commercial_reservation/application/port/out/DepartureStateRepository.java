package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DepartureStateRepository {

    Optional<DepartureState> find(UUID tenantId, UUID departureId);

    void save(DepartureState state);

    // Spec 027: moves the start and the estimated end of the row and its marker; it never writes the held seats or the status.
    void moveSchedule(UUID tenantId, UUID departureId, Instant startsAt, Instant estimatedEndsAt, Instant occurredAt);
}
