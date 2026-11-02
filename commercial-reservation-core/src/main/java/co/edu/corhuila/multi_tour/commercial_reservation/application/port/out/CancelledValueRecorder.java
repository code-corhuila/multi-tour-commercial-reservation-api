package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;

// Hands a value to the treatment of HU-COM-016, which owns it by the cause of the cancellation (010 X-7, OPEN where the cause is
// stored). The value is never an excess and never reaches the credit balance. A double until that story exists (DD-5).
public interface CancelledValueRecorder {

    enum Origin { APPROVED_AFTER_CANCELLATION, PENDING_TREATMENT_AFTER_EXPIRY }

    void record(UUID tenantId, UUID reservationId, Cents value, Origin origin);
}
