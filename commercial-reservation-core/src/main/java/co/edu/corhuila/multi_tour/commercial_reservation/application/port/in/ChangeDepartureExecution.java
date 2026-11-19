package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExecutionChange;
import java.util.UUID;

// G-API4-1 (RN-EJE-001, CA-029): the start of a departure moves its confirmed and fully paid reservations into execution, the end completes the ones in
// execution. The key makes a retry safe: a repeat answers the stored counts and changes nothing.
public interface ChangeDepartureExecution {
    ExecutionChange start(UUID tenantId, UUID salidaId, String key);

    ExecutionChange end(UUID tenantId, UUID salidaId, String key);
}
