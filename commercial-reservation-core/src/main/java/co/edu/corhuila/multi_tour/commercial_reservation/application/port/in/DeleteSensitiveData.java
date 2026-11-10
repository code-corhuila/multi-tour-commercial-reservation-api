package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeletionResult;
import java.util.UUID;

// HU-COM-019: the deletion of the sensitive data of one reservation, called by the retention job with a service token; the tenant is the one of the path.
public interface DeleteSensitiveData {

    DeletionResult delete(UUID tenantId, UUID reservationId);
}
