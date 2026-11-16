package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelledDepartureOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OpenReschedulingCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SalidaEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.HandleCancelledDeparture;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.OpenRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.VoidReschedulingProposals;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.util.List;
import java.util.UUID;

// Spec 023 (RN-EJE-002, FINAL-010, FINAL-011): a cancelled departure starts one episode of rescheduling per affected reservation, counted from the instant of the event, and the
// pending proposals that point to it are void. It composes OpenRescheduling and VoidReschedulingProposals (D-01), which are idempotent by reservation and departure, so the same
// event twice, or the saga that opened the episodes first, creates nothing more. Each reservation is its own transaction (D-03; DP-23.3: pages of 100 by keyset). The event is marked
// as received only after every reservation has its episode (D-02): one failure leaves it unmarked and answers RETRY_LATER, and the redelivery continues. A departure whose row is
// not CANCELLED (a late event over a final departure) only marks the event. No tenant status is read (I1), and only ids and instants are handled: no personal data.
public class HandleCancelledDepartureUseCase implements HandleCancelledDeparture {
    static final int PAGE = 100;

    private final DepartureStateRepository departures;
    private final ProcessedEvents processed;
    private final DepartureReservationReader reservations;
    private final OpenRescheduling opening;
    private final VoidReschedulingProposals voiding;
    private final PaymentTransaction transaction;

    public HandleCancelledDepartureUseCase(DepartureStateRepository departures, ProcessedEvents processed, DepartureReservationReader reservations, OpenRescheduling opening,
            VoidReschedulingProposals voiding, PaymentTransaction transaction) {
        this.departures = departures;
        this.processed = processed;
        this.reservations = reservations;
        this.opening = opening;
        this.voiding = voiding;
        this.transaction = transaction;
    }

    @Override
    public CancelledDepartureOutcome handle(SalidaEvent.Cancelled event) {
        boolean cancelled = departures.find(event.tenantId(), event.departureId()).filter(row -> row.status() == DepartureStatus.CANCELLED).isPresent();
        if (cancelled) {
            try {
                openEpisodes(event);
                voiding.voidProposals(event.tenantId(), event.departureId(), event.occurredAt());
            } catch (RuntimeException failed) {
                return CancelledDepartureOutcome.RETRY_LATER;
            }
        }
        transaction.inTransaction(() -> {
            processed.record(event.eventId());
            return null;
        });
        return CancelledDepartureOutcome.DONE;
    }

    private void openEpisodes(SalidaEvent.Cancelled event) {
        UUID after = null;
        while (true) {
            List<UUID> page = reservations.findAliveByDeparture(event.tenantId(), event.departureId(), after, PAGE);
            for (UUID reservationId : page) {
                opening.open(new OpenReschedulingCommand(event.tenantId(), reservationId, event.departureId(), event.occurredAt()));
            }
            if (page.size() < PAGE) return;
            after = page.get(page.size() - 1);
        }
    }
}
