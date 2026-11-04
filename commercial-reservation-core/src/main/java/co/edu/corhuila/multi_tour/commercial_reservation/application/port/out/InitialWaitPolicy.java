package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;

// RN-01 (RN-RES-006): the initial wait in hours is payment_condition.initial_wait_hours of the type of the reservation, the row of the
// service before the row of the tenant. Empty when the tenant has none: no number is invented and no deadline is written.
public interface InitialWaitPolicy {
    Optional<Integer> initialWaitHours(UUID tenantId, Reservation reservation);
}
