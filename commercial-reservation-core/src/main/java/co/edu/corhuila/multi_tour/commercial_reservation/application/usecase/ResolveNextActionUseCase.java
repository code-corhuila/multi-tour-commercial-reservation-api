package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ResolveNextAction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OpenProposalReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NextCustomerAction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

// The deadline holds when there is no open deadline (G-DL), when it is frozen, or when now is before its effective end.
public class ResolveNextActionUseCase implements ResolveNextAction {

    private final PaymentDeadlineReader deadlines;
    private final DepartureReader departures;
    private final OpenProposalReader proposals;
    private final Clock clock;

    public ResolveNextActionUseCase(PaymentDeadlineReader deadlines, DepartureReader departures, OpenProposalReader proposals,
            Clock clock) {
        this.deadlines = deadlines;
        this.departures = departures;
        this.proposals = proposals;
        this.clock = clock;
    }

    @Override
    public NextCustomerAction forRow(UUID tenantId, ReservationListItem row) {
        return NextCustomerAction.of(row.reservationStatus(), row.pendingBalance(), row.latestProof(),
                holds(tenantId, row.reservationId(), row.departureStartsAt()), proposals.isOpen(tenantId, row.reservationId()));
    }

    @Override
    public NextCustomerAction forReservation(Reservation reservation) {
        Instant start = departures.find(reservation.tenantId(), reservation.departureId()).map(DepartureState::startsAt).orElse(null);
        Optional<ValidationStatus> latest = reservation.attempts().stream().max(Comparator.comparing(PaymentAttempt::registeredAt))
                .map(PaymentAttempt::validationStatus);
        return NextCustomerAction.of(reservation.reservationStatus(), reservation.pendingBalance(), latest,
                holds(reservation.tenantId(), reservation.reservationId(), start),
                proposals.isOpen(reservation.tenantId(), reservation.reservationId()));
    }

    private boolean holds(UUID tenantId, UUID reservationId, Instant departureStart) {
        Optional<PaymentDeadline> open = deadlines.openDeadline(tenantId, reservationId);
        return open.isEmpty() || open.get().frozen() || clock.instant().isBefore(open.get().effective(departureStart));
    }
}
