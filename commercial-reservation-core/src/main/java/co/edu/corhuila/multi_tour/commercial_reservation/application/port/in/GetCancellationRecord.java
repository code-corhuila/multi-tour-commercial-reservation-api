package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import java.util.UUID;

// HU-COM-013 AC-13.10: the record of a cancellation, read whole.
public interface GetCancellationRecord {
    CancellationRecord get(PaymentActor actor, UUID reservationId);
}
