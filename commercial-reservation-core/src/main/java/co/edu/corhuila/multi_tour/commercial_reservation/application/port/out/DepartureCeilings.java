package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CeilingState;
import java.util.Optional;
import java.util.UUID;

// The effective capacity of the departures of a tenant. findForUpdate locks the row until the end of the transaction of the caller.
public interface DepartureCeilings {

    Optional<CeilingState> find(UUID tenantId, UUID salidaId);

    Optional<CeilingState> findForUpdate(UUID tenantId, UUID salidaId);

    void setCeiling(UUID tenantId, UUID salidaId, int ceiling);
}
