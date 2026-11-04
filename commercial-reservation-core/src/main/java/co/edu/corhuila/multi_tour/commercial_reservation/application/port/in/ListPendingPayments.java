package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PendingPaymentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;

// The reservations pending payment of the tenant of the actor, frozen deadlines left out (AC-11.11). A null kind lists every kind.
public interface ListPendingPayments {
    PendingPaymentPage list(PaymentActor actor, DeadlineKind kind, int page, int limit);
}
