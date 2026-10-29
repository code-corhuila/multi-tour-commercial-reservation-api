package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import java.util.Optional;
import java.util.UUID;

public interface DepartureStateRepository {

    Optional<DepartureState> find(UUID tenantId, UUID departureId);

    void save(DepartureState state);
}
