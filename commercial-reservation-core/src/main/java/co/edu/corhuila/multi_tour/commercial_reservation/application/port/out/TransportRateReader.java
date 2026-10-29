package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.Optional;
import java.util.UUID;

// The parametrized transport fare per person of a tour (plan D-04, RN-TRA-002). Empty when none exists; where it lives
// is DP-03, so the adapter is a double until that closes.
public interface TransportRateReader {

    Optional<Cents> perPersonCents(UUID tenantId, UUID tourId, UUID departureId);
}
