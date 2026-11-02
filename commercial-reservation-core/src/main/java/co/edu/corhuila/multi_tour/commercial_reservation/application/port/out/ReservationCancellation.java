package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.time.Instant;
import java.util.UUID;

// The cancellation of HU-COM-011: it owns the status change, its cause and the event ReservationCancelled (010 G-H). Called after
// the decision is saved, in the same transaction. A double until that story exists (DD-9).
public interface ReservationCancellation {

    void cancelForNonPayment(UUID tenantId, UUID reservationId, Instant now);
}
