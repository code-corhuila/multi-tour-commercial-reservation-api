package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import java.util.UUID;

// The deadline with its effective instant (AC-11.6). A customer reads only the deadline of an own reservation (AC-11.8).
public interface GetPaymentDeadline {
    DeadlineView get(PaymentActor actor, UUID reservationId);
}
