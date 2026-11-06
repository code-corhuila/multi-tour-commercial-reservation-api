package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPayments;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetOwnReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservationPayments;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class ListReservationPaymentsUseCase implements ListReservationPayments {

    private final GetReservation get;
    private final GetOwnReservation getOwn;

    public ListReservationPaymentsUseCase(GetReservation get, GetOwnReservation getOwn) {
        this.get = get;
        this.getOwn = getOwn;
    }

    @Override
    public ReservationPayments list(PaymentActor actor, UUID reservationId, int page, int limit) {
        // The own scope for a customer, so a reservation of another customer looks missing.
        Reservation reservation = actor.role() == PaymentActor.Role.FINAL_CUSTOMER ? getOwn.get(actor.tenantId(), actor.actorId(), reservationId)
                : get.get(actor.tenantId(), reservationId);
        List<PaymentAttempt> sorted = reservation.attempts().stream().sorted(Comparator.comparing(PaymentAttempt::registeredAt).reversed()).toList();
        List<PaymentAttempt> data = sorted.stream().skip((page - 1L) * limit).limit(limit).toList();
        return new ReservationPayments(reservation, data, sorted.size());
    }
}
