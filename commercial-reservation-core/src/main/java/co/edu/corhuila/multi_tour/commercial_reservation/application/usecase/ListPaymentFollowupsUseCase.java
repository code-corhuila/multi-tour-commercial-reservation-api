package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.FollowupPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPaymentFollowups;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import java.util.UUID;

// DP-02: the reading roles see the history; a reservation of another tenant looks missing (AC-11.10).
public class ListPaymentFollowupsUseCase implements ListPaymentFollowups {

    private final ReservationRepository reservations;
    private final PaymentFollowupRepository followups;

    public ListPaymentFollowupsUseCase(ReservationRepository reservations, PaymentFollowupRepository followups) {
        this.reservations = reservations;
        this.followups = followups;
    }

    @Override
    public FollowupPage list(PaymentActor actor, UUID reservationId, int page, int limit) {
        PaymentAuthorization.requireReadDeadlines(actor);
        reservations.findByTenantAndId(actor.tenantId(), reservationId).orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return followups.page(actor.tenantId(), reservationId, page, limit);
    }
}
