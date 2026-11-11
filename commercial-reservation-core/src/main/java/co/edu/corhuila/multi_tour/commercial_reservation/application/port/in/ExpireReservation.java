package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExpiryResult;
import java.util.UUID;

// The job asks and the service decides (RN-07): the condition is re-checked at the call, and an expiry is cancelled once.
public interface ExpireReservation {
    ExpiryResult expire(UUID tenantId, UUID reservationId);
}
