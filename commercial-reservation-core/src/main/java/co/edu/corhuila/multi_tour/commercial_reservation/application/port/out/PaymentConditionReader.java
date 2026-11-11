package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.UUID;

// The minimum deposit of the service in cents, resolved on the final value after discounts and rounded up to the cent
// (010 DP-01); empty when the service has none. The adapter reads payment_condition.minimum_deposit_bp.
public interface PaymentConditionReader {

    java.util.Optional<Cents> minimumDepositFor(UUID tenantId, Reservation reservation);
}
