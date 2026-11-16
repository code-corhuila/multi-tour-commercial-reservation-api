package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelledDepartureOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SalidaEvent;

// Spec 023: what follows the mirror of SalidaCancelled: the episodes of rescheduling of the alive reservations and the void proposals. Called by the route after the mirror.
public interface HandleCancelledDeparture {
    CancelledDepartureOutcome handle(SalidaEvent.Cancelled event);
}
