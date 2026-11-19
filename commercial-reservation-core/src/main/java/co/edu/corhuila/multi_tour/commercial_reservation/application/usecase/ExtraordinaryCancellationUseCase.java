package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExtraordinaryOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OpenReschedulingCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ApplyExtraordinaryCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.OpenRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExtraordinaryCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.Optional;

// Spec 025 (RN-EJE-002, CA-008A, agreement of 2026-11-14): an extraordinary cancellation during execution leads to a decision, not to an automatic 100 percent. The decision is
// the episode of spec 018 (propose a new departure, or declare there is none; only a rejection, a silence or no alternative ends in the full refund), so this use case only opens it
// through OpenRescheduling, counted from the event instant, and never cancels the reservation or touches a refund. The episode and the processed mark commit together. A reservation
// that does not exist for the tenant, or is not on the departure of the event, leaves the event unmarked; a cancelled or completed one is marked and ignored. The justification never
// reaches this class. No tenant status is read (I1).
public class ExtraordinaryCancellationUseCase implements ApplyExtraordinaryCancellation {

    private final ProcessedEvents processed;
    private final ReservationRepository reservations;
    private final ReschedulingRepository episodes;
    private final OpenRescheduling opening;
    private final PaymentTransaction transaction;

    public ExtraordinaryCancellationUseCase(ProcessedEvents processed, ReservationRepository reservations, ReschedulingRepository episodes, OpenRescheduling opening,
            PaymentTransaction transaction) {
        this.processed = processed;
        this.reservations = reservations;
        this.episodes = episodes;
        this.opening = opening;
        this.transaction = transaction;
    }

    @Override
    public ExtraordinaryOutcome apply(ExtraordinaryCancellation cancellation) {
        return transaction.inTransaction(() -> {
            if (processed.wasProcessed(cancellation.eventId())) return ExtraordinaryOutcome.DUPLICATE;
            Optional<Reservation> found = reservations.findByTenantAndId(cancellation.tenantId(), cancellation.reservationId())
                    .filter(reservation -> reservation.departureId().equals(cancellation.salidaId()));
            if (found.isEmpty()) return ExtraordinaryOutcome.UNKNOWN_RESERVATION;
            ReservationStatus status = found.get().reservationStatus();
            ExtraordinaryOutcome outcome;
            if (status == ReservationStatus.CANCELLED || status == ReservationStatus.COMPLETED) {
                outcome = ExtraordinaryOutcome.IGNORED_FINAL;
            } else if (episodes.findByIncident(cancellation.tenantId(), cancellation.reservationId(), cancellation.salidaId()).isPresent()) {
                outcome = ExtraordinaryOutcome.ALREADY_OPEN;
            } else {
                open(cancellation);
                outcome = ExtraordinaryOutcome.EPISODE_OPENED;
            }
            processed.record(cancellation.eventId());
            return outcome;
        });
    }

    // The only refusal left once the reservation is known to be on the departure is an instant in the future: the event is wrong, not the reservation.
    private void open(ExtraordinaryCancellation cancellation) {
        try {
            opening.open(new OpenReschedulingCommand(cancellation.tenantId(), cancellation.reservationId(), cancellation.salidaId(), cancellation.occurredAt()));
        } catch (BusinessRuleViolation future) {
            throw new DomainException("occurredAt", "must not be in the future");
        }
    }
}
