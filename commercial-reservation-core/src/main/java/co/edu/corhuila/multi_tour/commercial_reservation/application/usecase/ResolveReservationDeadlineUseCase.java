package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ResolveReservationDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// The effective deadline of the open one, as the deadline read derives it (PaymentDeadline.effective). A frozen deadline is still the
// open one (RN-17): freezing stops it from expiring, it does not remove it.
public class ResolveReservationDeadlineUseCase implements ResolveReservationDeadline {

    private final PaymentDeadlineReader deadlines;
    private final DepartureReader departures;

    public ResolveReservationDeadlineUseCase(PaymentDeadlineReader deadlines, DepartureReader departures) {
        this.deadlines = deadlines;
        this.departures = departures;
    }

    @Override
    public Optional<Instant> forRow(UUID tenantId, ReservationListItem row) {
        return inForce(tenantId, row.reservationId(), row.departureStartsAt());
    }

    @Override
    public Optional<Instant> forReservation(Reservation reservation) {
        Instant start = departures.find(reservation.tenantId(), reservation.departureId()).map(DepartureState::startsAt).orElse(null);
        return inForce(reservation.tenantId(), reservation.reservationId(), start);
    }

    private Optional<Instant> inForce(UUID tenantId, UUID reservationId, Instant departureStart) {
        return deadlines.openDeadline(tenantId, reservationId).map(open -> open.effective(departureStart));
    }
}
