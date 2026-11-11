package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.FollowupPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import java.util.UUID;

// The history of a reservation, newest first. The staff and the reading roles; never the customer.
public interface ListPaymentFollowups {
    FollowupPage list(PaymentActor actor, UUID reservationId, int page, int limit);
}
