package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// HU-COM-008: the payment deadline in force of a row of the list or of one reservation (RN-02: the extension counts, never past the
// departure start), so the portal only displays it. Empty when the reservation has no open deadline (G-DL).
public interface ResolveReservationDeadline {

    Optional<Instant> forRow(UUID tenantId, ReservationListItem row);

    Optional<Instant> forReservation(Reservation reservation);
}
