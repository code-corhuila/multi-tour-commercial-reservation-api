package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureCeiling;
import java.util.UUID;

// HU-COM-012 amendment 2026-11-10: the saga change-departure-vehicles lowers the ceiling of a departure and restores it. The key makes a retry safe.
public interface SetDepartureCeiling {
    DepartureCeiling set(UUID tenantId, UUID salidaId, int ceiling, String key);
}
