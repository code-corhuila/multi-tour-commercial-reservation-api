package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationQuote.Reason;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ModifyReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingDifferenceDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityAllocated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityReleased;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingReservationDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ModificationOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationChanged;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

// RN-01 to RN-07, RN-09; AC-12.1 to AC-12.5, AC-12.11, AC-12.12. One transaction: the terms are read, the seats are held, released or moved
// by conditional updates, the aggregate recomputes, the situation is settled, the difference deadline is written and the excess is handed to
// the decision, never dropped. The role is checked before any load; a reservation of another tenant looks missing. The history record, the
// events and the idempotency of the route arrive with the adapters (declared in the pull request).
public class ModifyReservationUseCase implements ModifyReservation {

    static final int MAXIMUM_REASON = 500;

    private final ReservationRepository reservations;
    private final DepartureReader departures;
    private final ChangePolicyReader policies;
    private final PaymentConditionReader conditions;
    private final SalidaCapacityHolder holder;
    private final ExcessDecisionOpener decisions;
    private final PendingDifferenceDeadlines deadlines;
    private final ReservationModifications history;
    private final PaymentEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public ModifyReservationUseCase(ReservationRepository reservations, DepartureReader departures, ChangePolicyReader policies,
            PaymentConditionReader conditions, SalidaCapacityHolder holder, ExcessDecisionOpener decisions, PendingDifferenceDeadlines deadlines,
            ReservationModifications history, PaymentEventOutbox outbox, PaymentTransaction transaction, Clock clock) {
        this.reservations = reservations;
        this.departures = departures;
        this.policies = policies;
        this.conditions = conditions;
        this.holder = holder;
        this.decisions = decisions;
        this.deadlines = deadlines;
        this.history = history;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public ReservationModificationRecord modify(PaymentActor actor, UUID reservationId, ModificationRequest request, String reason) {
        ModificationAuthorization.requireWrite(actor);
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty() || why.length() > MAXIMUM_REASON) throw new MissingReservationDataException(List.of("reason"));
        return transaction.inTransaction(() -> {
            Reservation reservation = reservations.lockByTenantAndId(actor.tenantId(), reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(reservationId));
            Instant now = Instant.now(clock);
            DepartureState current = departures.find(reservation.tenantId(), reservation.departureId()).orElse(null);
            Reason refusal = ChangeTerms.refusal(
                    policies.policyFor(reservation.tenantId(), reservation.service().serviceId(), request.changeType()),
                    current == null ? null : current.startsAt(), now);
            if (refusal != null) throw new BusinessRuleViolation("the terms of the tour refuse the change: " + refusal);
            boolean dateChange = request.changeType() == ChangeType.DATE_CHANGE;
            requireShape(reservation, request, dateChange);
            DepartureState target = dateChange ? departures.find(reservation.tenantId(), request.departureId())
                    .filter(row -> row.serviceId().equals(reservation.service().serviceId()))
                    .orElseThrow(() -> new DomainException("departureId", "the departure must be one of the reserved tour")) : current;
            reservation.quoteModification(request.partySize(), reservation.service(), request.departureId(), request.companions(),
                    current.startsAt(), now);
            UUID previousSalida = reservation.departureId();
            int previousParty = reservation.people();
            moveSeats(reservation, request, dateChange, target);
            ReservationStatus status = reservation.reservationStatus();
            ModificationOutcome outcome = reservation.modify(request.partySize(), reservation.service(), request.departureId(),
                    request.companions(), target.startsAt(), now);
            reservation.settleSituation(conditions.minimumDepositFor(reservation.tenantId(), reservation).orElse(null));
            if (status == ReservationStatus.CONFIRMED && outcome.oldPendingBalance().amount() == 0 && outcome.newPendingBalance().amount() > 0)
                deadlines.record(reservation.tenantId(), reservation.reservationId(), differenceDue(reservation, target.startsAt(), now));
            if (outcome.excess().amount() > 0) decisions.open(reservation.tenantId(), reservation.reservationId(), outcome.excess(), now);
            reservations.save(reservation);
            ReservationModificationRecord record = new ReservationModificationRecord(UUID.randomUUID(), reservation.reservationId(),
                    request.changeType(), why, outcome, previousParty, reservation.people(), reservation.service().serviceId(), previousSalida,
                    reservation.departureId(), actor.actorId(), now);
            history.append(reservation.tenantId(), record);
            announce(reservation, record, previousSalida, now);
            return record;
        });
    }

    // AC-12.15: the change and, when the seats moved, the capacity events; values, party and codes only, never the reason or a name.
    private void announce(Reservation reservation, ReservationModificationRecord record, UUID previousSalida, Instant now) {
        boolean moved = !previousSalida.equals(record.newSalidaId());
        outbox.append(new ReservationChanged(reservation.tenantId(), reservation.reservationId(), record.changeType(), record.outcome().oldFinalValue(),
                record.outcome().newFinalValue(), record.outcome().oldPendingBalance(), record.outcome().newPendingBalance(), record.newPartySize(), moved,
                ReservationChanged.STAFF_CHANGE, record.registeredBy(), now));
        if (!reservation.seatHeld() || record.outcome().seatDelta() == 0 && !moved) return;
        UUID service = reservation.service().serviceId();
        if (moved || record.outcome().seatDelta() > 0)
            outbox.append(new CapacityAllocated(reservation.tenantId(), reservation.reservationId(), service, record.newSalidaId(),
                    CapacityAllocated.RESERVATION_KIND, now));
        if (moved || record.outcome().seatDelta() < 0)
            outbox.append(new CapacityReleased(reservation.tenantId(), reservation.reservationId(), service, previousSalida,
                    CapacityReleased.RESERVATION_MODIFIED, now));
    }

    // A modification keeps the departure and a date change keeps the party (plan section 2); a proof under review blocks the move (RN-07).
    private static void requireShape(Reservation reservation, ModificationRequest request, boolean dateChange) {
        if (dateChange && request.partySize() != reservation.people()) throw new DomainException("partySize", "a date change keeps the party");
        if (!dateChange && !reservation.departureId().equals(request.departureId()))
            throw new DomainException("departureId", "a modification keeps the departure; use a date change");
        if (dateChange && reservation.attempts().stream().anyMatch(a -> a.validationStatus() == ValidationStatus.IN_VALIDATION))
            throw new BusinessRuleViolation("a date change is refused while a proof is in validation");
    }

    // RN-06, RN-07: a reservation that holds seats holds only the delta (or moves them as one operation); one that holds none is only checked.
    private void moveSeats(Reservation reservation, ModificationRequest request, boolean dateChange, DepartureState target) {
        int delta = request.partySize() - reservation.people();
        if (reservation.seatHeld()) {
            boolean done = dateChange ? holder.move(reservation.tenantId(), reservation.reservationId(), reservation.departureId(),
                    target.departureId(), reservation.people())
                    : delta > 0 ? holder.hold(reservation.tenantId(), target.departureId(), delta) : true;
            if (!done) throw new BusinessRuleViolation("the departure has no seats for the change");
            if (!dateChange && delta < 0) holder.releaseSeats(reservation.tenantId(), reservation.reservationId(), -delta);
        } else if (target.seats().available() < request.partySize()) {
            throw new BusinessRuleViolation("the departure has no seats for the change");
        }
    }

    // D-06: the balance deadline (departure start minus the advance hours of a deposit) when it is still ahead; otherwise the departure start.
    private Instant differenceDue(Reservation reservation, Instant start, Instant now) {
        OptionalInt advance = reservation.expectedPaymentType() == PaymentType.DEPOSIT
                ? deadlines.balanceAdvanceHours(reservation.tenantId(), reservation.service().serviceId(), reservation.expectedPaymentType())
                : OptionalInt.empty();
        if (advance.isEmpty()) return start;
        Instant balance = start.minus(Duration.ofHours(advance.getAsInt()));
        return balance.isAfter(now) ? balance : start;
    }
}
