package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// HU-COM-013 RN-07, AC-13.6: is the reservation marked "Pendiente de respuesta de reprogramacion"? The mark belongs to HU-COM-018, so
// the only adapter answers "not marked" until it exists (DP-06, declared debt DD-5).
public interface ReprogrammingMarkReader {

    boolean isMarked(UUID tenantId, UUID reservationId);
}
