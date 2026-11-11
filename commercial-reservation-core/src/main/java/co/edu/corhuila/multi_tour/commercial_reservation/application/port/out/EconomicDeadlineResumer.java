package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.time.Instant;
import java.util.UUID;

// HU-COM-018 AC-18.1, HU-COM-020 AC-20.3: resumes the frozen economic deadlines of a reservation at an instant with the time they had left, never past the start of the departure, in the
// transaction of the caller. Only the rows frozen by the cause that the caller owns are touched; the adapter writes the effective deadline and clears the freeze of HU-COM-011.
public interface EconomicDeadlineResumer {

    void resume(UUID tenantId, UUID reservationId, Instant at, Instant departureStart);
}
