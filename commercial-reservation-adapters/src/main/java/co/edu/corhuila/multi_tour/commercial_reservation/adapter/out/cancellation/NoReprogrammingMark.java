package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cancellation;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReprogrammingMarkReader;
import java.util.UUID;

// DOCUMENTED STAND-IN (HU-COM-013 DP-06, declared debt DD-5): the mark "Pendiente de respuesta de reprogramacion" belongs to HU-COM-018 and
// does not exist yet, so no reservation is marked.
public final class NoReprogrammingMark implements ReprogrammingMarkReader {

    @Override
    public boolean isMarked(UUID tenantId, UUID reservationId) {
        return false;
    }
}
