package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExtendDeadlineCommand;

// The single extension of the initial payment deadline (RN-03). Administrator and Collaborator only.
public interface ExtendPaymentDeadline {
    DeadlineView extend(ExtendDeadlineCommand command);
}
