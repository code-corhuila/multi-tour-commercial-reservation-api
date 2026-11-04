package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;

// HU-COM-013: the cancellation the customer asks for, registered by the Administrator or the Collaborator.
public interface CancelReservation {
    CancellationRecord cancel(CancelReservationCommand command);
}
