package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;

// DP-05: the maximum extension in hours is payment_condition.additional_wait_hours (service, else tenant). Empty when not configured: no number is invented.
public interface PaymentExtensionPolicy {
    Optional<Integer> maximumHours(UUID tenantId, Reservation reservation);
}
