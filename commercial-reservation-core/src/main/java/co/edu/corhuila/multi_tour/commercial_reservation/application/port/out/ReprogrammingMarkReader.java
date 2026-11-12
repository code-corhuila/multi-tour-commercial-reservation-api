package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// HU-COM-013 RN-07, AC-13.6: is the reservation marked "Pendiente de respuesta de reprogramacion"? The mark is HU-COM-018's: a reservation is marked while
// it has an open rescheduling episode (JdbcReprogrammingMark; DP-06 and debt DD-5 are closed).
public interface ReprogrammingMarkReader {

    boolean isMarked(UUID tenantId, UUID reservationId);
}
