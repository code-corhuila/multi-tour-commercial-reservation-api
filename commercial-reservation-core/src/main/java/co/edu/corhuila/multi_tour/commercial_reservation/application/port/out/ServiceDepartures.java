package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import java.util.List;
import java.util.UUID;

// The capacity rows of one service, locked for the catalog update that recomputes them (ND-08, DP-D6-8). Used inside the transaction of
// that update: the rows stay locked until it commits, so a concurrent hold waits and then judges the new effective capacity.
public interface ServiceDepartures {

    List<DepartureState> lockAllOf(UUID tenantId, UUID serviceId);

    void save(DepartureState state);
}
