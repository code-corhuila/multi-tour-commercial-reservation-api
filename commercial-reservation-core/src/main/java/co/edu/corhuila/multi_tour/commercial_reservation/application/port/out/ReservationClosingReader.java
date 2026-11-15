package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Closing;
import java.util.Optional;
import java.util.UUID;

// HU-COM-019 plan D-03, DD-2: the definitive closing of a cancelled reservation, as the settlement of its financial decisions (HU-COM-016 refunds, HU-COM-017 credit
// balances) tells it. Empty means not closed or nothing to close; the anchor rule decides what that means.
public interface ReservationClosingReader {

    Optional<Closing> closingOf(UUID tenantId, UUID reservationId);
}
