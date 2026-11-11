package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordFollowupCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RecordPaymentFollowup;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Clock;
import java.time.Instant;

// RN-05, AC-11.5, AC-11.7: the follow-up is built first (so a missing note is named before anything is read), then appended. It touches
// neither the deadline nor the reservation, and it emits no event (DP-07).
public class RecordPaymentFollowupUseCase implements RecordPaymentFollowup {

    private final ReservationRepository reservations;
    private final PaymentFollowupRepository followups;
    private final IdGenerator ids;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public RecordPaymentFollowupUseCase(ReservationRepository reservations, PaymentFollowupRepository followups, IdGenerator ids,
            PaymentTransaction transaction, Clock clock) {
        this.reservations = reservations;
        this.followups = followups;
        this.ids = ids;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public PaymentFollowup record(RecordFollowupCommand command) {
        PaymentAuthorization.requireFollowUp(command.actor());
        return transaction.inTransaction(() -> {
            Reservation reservation = reservations.findByTenantAndId(command.actor().tenantId(), command.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));
            PaymentFollowup followup = new PaymentFollowup(ids.newId(), reservation.tenantId(), reservation.reservationId(),
                    command.actor().actorId(), Instant.now(clock), command.note());
            if (reservation.reservationStatus() != ReservationStatus.PENDING_PAYMENT)
                throw new BusinessRuleViolation("a follow-up applies to a reservation pending payment only");
            followups.append(followup);
            return followup;
        });
    }
}
