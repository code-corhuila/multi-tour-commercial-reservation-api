package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExtraordinaryOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExtraordinaryCancellation;

// Spec 025: applies ExtraordinaryCancellationRecorded once. It opens the decision of RN-EJE-002 (the episode of spec 018); it never cancels the reservation.
public interface ApplyExtraordinaryCancellation {
    ExtraordinaryOutcome apply(ExtraordinaryCancellation cancellation);
}
