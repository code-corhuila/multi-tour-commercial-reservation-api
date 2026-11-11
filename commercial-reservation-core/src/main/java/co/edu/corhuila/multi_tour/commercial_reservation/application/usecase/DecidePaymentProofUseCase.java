package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApproveProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DecidePaymentProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder.Origin;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityReleased;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingPaymentDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The decision of a proof (plan D-02, D-10): the aggregate decides, this class orders what surrounds it in one transaction.
// The decision is saved before the cancellation of HU-COM-011 runs, so the status it writes is not overwritten.
public class DecidePaymentProofUseCase implements DecidePaymentProof {

    private final ReservationRepository reservations;
    private final PaymentDeadlineReader deadlines;
    private final DepartureReader departures;
    private final PaymentConditionReader conditions;
    private final SalidaCapacityHolder holder;
    private final ReservationCancellation cancellation;
    private final CancelledValueRecorder cancelledValues;
    private final CreditBalanceRecorder credit;
    private final PaymentEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public DecidePaymentProofUseCase(ReservationRepository reservations, PaymentDeadlineReader deadlines, DepartureReader departures, PaymentConditionReader conditions,
            SalidaCapacityHolder holder, ReservationCancellation cancellation, CancelledValueRecorder cancelledValues,
            CreditBalanceRecorder credit, PaymentEventOutbox outbox, PaymentTransaction transaction, Clock clock) {
        this.reservations = reservations;
        this.deadlines = deadlines;
        this.departures = departures;
        this.conditions = conditions;
        this.holder = holder;
        this.cancellation = cancellation;
        this.cancelledValues = cancelledValues;
        this.credit = credit;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public DecidedProof approve(ApproveProofCommand command) {
        PaymentProofAuthorization.requireDecide(command.actor());
        return recordApproval(command.actor().tenantId(), command.actor().actorId(), command.attemptId(), command.verifiedAmountCents(),
                command.reason());
    }

    // The work of an approval, whoever opens the door: the human route checks the permission first, the saga step 2 is trusted.
    DecidedProof recordApproval(UUID tenantId, UUID reviewer, UUID attemptId, Long verifiedAmountCents, String reason) {
        if (verifiedAmountCents == null) throw new MissingPaymentDataException(List.of("verifiedAmountCents"));
        Cents verified = Cents.of("verifiedAmountCents", verifiedAmountCents);
        return transaction.inTransaction(() -> {
            Reservation reservation = lock(tenantId, attemptId);
            Instant now = Instant.now(clock);
            Optional<PaymentDeadline> deadline = deadlines.openDeadline(tenantId, reservation.reservationId());
            boolean expired = expired(deadline, reservation, now);
            Cents minimum = minimumFor(reservation);
            ProofDecision decision = reservation.approve(attemptId, verified, reason, minimum, reviewer, now);
            if (decision.treatment().amount() > 0) {
                cancelledValues.record(tenantId, reservation.reservationId(), decision.treatment(), Origin.APPROVED_AFTER_CANCELLATION);
            } else if (decision.excess().amount() > 0) {
                credit.recordExcess(tenantId, reservation.reservationId(), attemptId, decision.excess());
            }
            boolean cancels = expired && decision.treatment().amount() == 0 && !conditionMet(deadline.get().kind(), reservation);
            Cents validated = new Cents(reservation.finalValue().amount() - reservation.pendingBalance().amount());
            if (cancels) releaseSeat(reservation, CapacityReleased.CANCELLED_FOR_NON_PAYMENT, now);
            persist(reservation);
            if (cancels) {
                cancellation.cancelForNonPayment(tenantId, reservation.reservationId(), now);
                cancelledValues.record(tenantId, reservation.reservationId(), validated, Origin.PENDING_TREATMENT_AFTER_EXPIRY);
            }
            return new DecidedProof(reload(reservation), decision.attempt(), decision.applied(), decision.excess());
        });
    }

    @Override
    public DecidedProof reject(RejectProofCommand command) {
        PaymentProofAuthorization.requireDecide(command.actor());
        PaymentActor actor = command.actor();
        return transaction.inTransaction(() -> {
            Reservation reservation = lock(actor.tenantId(), command.attemptId());
            Instant now = Instant.now(clock);
            boolean expired = expired(deadlines.openDeadline(actor.tenantId(), reservation.reservationId()), reservation, now);
            var rejected = reservation.reject(command.attemptId(), command.reason(), actor.actorId(), now);
            if (expired) releaseSeat(reservation, CapacityReleased.CANCELLED_FOR_NON_PAYMENT, now);
            else if (reservation.shouldReleaseSeatAfterRejection()) releaseSeat(reservation, CapacityReleased.FIRST_PAYMENT_REJECTED, now);
            persist(reservation);
            if (expired) cancellation.cancelForNonPayment(actor.tenantId(), reservation.reservationId(), now);
            return new DecidedProof(reload(reservation), rejected, new Cents(0), new Cents(0));
        });
    }

    // The deadline in force (RN-02: the extension counts, never past the departure start); a frozen one does not expire (RN-17), as in the expiry job.
    private boolean expired(Optional<PaymentDeadline> deadline, Reservation reservation, Instant now) {
        return deadline.map(found -> {
            if (found.frozen()) return false;
            Instant start = departures.find(reservation.tenantId(), reservation.departureId()).map(DepartureState::startsAt).orElse(null);
            return !now.isBefore(found.effective(start));
        }).orElse(false);
    }

    // Another tenant's proof looks missing (AC-10.17). The row lock of the reservation is taken before the state of the proof is read for the
    // decision (RNF-06): a second decision waits here and then finds the proof already decided, which the aggregate refuses.
    private Reservation lock(UUID tenantId, UUID attemptId) {
        return reservations.lockByTenantAndAttempt(tenantId, attemptId)
                .filter(found -> found.tenantId().equals(tenantId))
                .orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
    }

    // Only a deposit has a minimum; the adapter resolves it in cents, rounded up (010 DP-01).
    private Cents minimumFor(Reservation reservation) {
        if (reservation.expectedPaymentType() != PaymentType.DEPOSIT) return null;
        return conditions.minimumDepositFor(reservation.tenantId(), reservation)
                .orElseThrow(() -> new BusinessRuleViolation("the minimum deposit is not configured"));
    }

    // RN-18, RN-19: the condition of the expired deadline is the confirmation for the initial one and the balance for the others.
    private static boolean conditionMet(DeadlineKind kind, Reservation reservation) {
        return kind == DeadlineKind.INITIAL_PAYMENT ? reservation.reservationStatus() != ReservationStatus.PENDING_PAYMENT
                : reservation.pendingBalance().amount() == 0;
    }

    // Zero seats released means nothing was held and no event is written.
    private void releaseSeat(Reservation reservation, String reason, Instant now) {
        if (holder.release(reservation.tenantId(), reservation.reservationId()) > 0) reservation.releaseSeat(reason, now);
    }

    private void persist(Reservation reservation) {
        reservations.save(reservation);
        reservation.pullEvents().forEach(outbox::append);
    }

    private Reservation reload(Reservation reservation) {
        return reservations.findByTenantAndId(reservation.tenantId(), reservation.reservationId()).orElse(reservation);
    }
}
