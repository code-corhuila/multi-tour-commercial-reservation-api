package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;

// The reservation as it was created, for the answer of an idempotent repeat and the read of the creation (plan D-11, D-08): the
// values fixed at creation, never recomputed. An absent or foreign reservation is the same empty answer (RN-09).
public interface CreatedReservationReader {
    Optional<Reservation> findAsCreated(UUID tenantId, UUID reservationId);
}
