package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PendingPaymentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPendingPayments;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingPaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;

// DP-02: the reading roles list the reservations pending payment of their tenant; the query leaves frozen deadlines out (AC-11.11).
public class ListPendingPaymentsUseCase implements ListPendingPayments {

    private final PendingPaymentQueries queries;

    public ListPendingPaymentsUseCase(PendingPaymentQueries queries) {
        this.queries = queries;
    }

    @Override
    public PendingPaymentPage list(PaymentActor actor, DeadlineKind kind, int page, int limit) {
        PaymentAuthorization.requireReadDeadlines(actor);
        return queries.page(actor.tenantId(), kind, page, limit);
    }
}
