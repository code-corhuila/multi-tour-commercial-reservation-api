package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReschedulingView;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import java.util.UUID;

// HU-COM-018 AC-18.16, AC-18.18: staff reads every episode of the tenant; a Final Customer reads only the episodes of their own reservations, and a foreign one looks missing.
public interface ReadReschedulings {
    RefundPage<ReschedulingView> page(PaymentActor actor, UUID reservationId, UUID departureId, EpisodeState state, int page, int limit);

    ReschedulingView get(PaymentActor actor, UUID episodeId);
}
