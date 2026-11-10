package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Closing;
import java.util.Optional;
import java.util.UUID;

// HU-COM-019 plan D-03, declared debt DD-2: the definitive closing of a cancelled reservation belongs to HU-COM-016 and HU-COM-017. Until they give it, the adapter
// answers empty and a test uses a double.
public interface ReservationClosingReader {

    Optional<Closing> closingOf(UUID tenantId, UUID reservationId);
}
