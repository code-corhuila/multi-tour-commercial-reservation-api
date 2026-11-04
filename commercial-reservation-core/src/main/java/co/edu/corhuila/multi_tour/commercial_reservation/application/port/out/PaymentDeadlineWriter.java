package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.time.Instant;
import java.util.UUID;

// Writes the single extension on the initial deadline row, in the transaction of the caller (RN-03).
public interface PaymentDeadlineWriter {
    void saveExtension(UUID tenantId, UUID reservationId, int hours, Instant grantedAt, UUID grantedBy);
}
