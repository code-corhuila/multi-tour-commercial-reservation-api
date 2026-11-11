package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.retention;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationClosingReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Closing;
import java.util.Optional;
import java.util.UUID;

// DOCUMENTED STAND-IN (declared debt DD-2 of HU-COM-019): no story records the definitive closing of a cancelled reservation yet, so none is ever known. A cancelled
// reservation with values to treat has no anchor and is not deleted; one with nothing to treat counts from its cancellation. The data is kept, never deleted early.
public final class DeferredReservationClosing implements ReservationClosingReader {

    @Override
    public Optional<Closing> closingOf(UUID tenantId, UUID reservationId) {
        return Optional.empty();
    }
}
