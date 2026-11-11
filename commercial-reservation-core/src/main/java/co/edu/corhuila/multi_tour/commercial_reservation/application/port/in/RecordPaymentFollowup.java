package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordFollowupCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;

// Appends a follow-up to a reservation pending payment. Administrator and Collaborator only.
public interface RecordPaymentFollowup {
    PaymentFollowup record(RecordFollowupCommand command);
}
