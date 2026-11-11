package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.UUID;

// AC-11.6, AC-11.8, AC-11.10: the staff read it; a Final Customer reads it only on an own reservation. Another tenant, another
// customer or a reservation without a deadline row all answer as a missing one.
public class GetPaymentDeadlineUseCase implements GetPaymentDeadline {

    private final ReservationRepository reservations;
    private final PaymentDeadlineReader deadlines;
    private final DepartureReader departures;

    public GetPaymentDeadlineUseCase(ReservationRepository reservations, PaymentDeadlineReader deadlines, DepartureReader departures) {
        this.reservations = reservations;
        this.deadlines = deadlines;
        this.departures = departures;
    }

    @Override
    public DeadlineView get(PaymentActor actor, UUID reservationId) {
        if (actor.role() != Role.FINAL_CUSTOMER) PaymentAuthorization.requireReadDeadlines(actor);
        Reservation reservation = reservations.findByTenantAndId(actor.tenantId(), reservationId)
                .filter(found -> actor.role() != Role.FINAL_CUSTOMER || PaymentProofAuthorization.isOwner(actor, found))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        PaymentDeadline deadline = deadlines.openDeadline(reservation.tenantId(), reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        Instant start = departures.find(reservation.tenantId(), reservation.departureId()).map(DepartureState::startsAt).orElse(null);
        return new DeadlineView(reservationId, deadline, start, deadline.effective(start));
    }
}
