package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NextCustomerAction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.UUID;

// HU-COM-014 AC-14.8, RN-06: the action the Final Customer may take next, decided by the service for a row of the list or for one
// reservation, so the portal only displays it.
public interface ResolveNextAction {

    NextCustomerAction forRow(UUID tenantId, ReservationListItem row);

    NextCustomerAction forReservation(Reservation reservation);
}
