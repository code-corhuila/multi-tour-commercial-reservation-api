package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExpiryVerdict;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.UUID;

// The answer to the job: expired with the cause, or not expired with the reason. Nothing changed when expired is false.
public record ExpiryResult(UUID reservationId, boolean expired, ExpiryVerdict reason, CancellationCause cause, ReservationStatus status) {
}
