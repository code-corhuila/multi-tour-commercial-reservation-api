package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.List;
import java.util.UUID;

// Spec 023 plan: the ids of the alive reservations (pending payment or confirmed) of a departure, by keyset on the id so a large departure is read in pages. Led by the tenant:
// the reservations of another tenant are never read.
public interface DepartureReservationReader {
    List<UUID> findAliveByDeparture(UUID tenantId, UUID departureId, UUID afterReservationId, int limit);
}
