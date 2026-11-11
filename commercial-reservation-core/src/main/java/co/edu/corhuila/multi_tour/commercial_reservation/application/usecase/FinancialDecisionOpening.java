package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.BusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// HU-COM-016 plan D-05, D-08; AC-16.15, AC-16.16: the real adapter of the four ports that 010, 011, 012 and 013 left as doubles. It runs inside the
// transaction of the story that hands the value over. One decision opens per recognized value; a value recognized again for an open decision updates its
// amount; a late value on a decided refund becomes one more automatic authorization of that decision, never a second 24-hour decision (DP-11).
public class FinancialDecisionOpening implements FinancialDecisionOpener, ExcessDecisionOpener, ExcessTreatment, CancelledValueRecorder {

    private final FinancialDecisionRepository decisions;
    private final RefundAuthorizationRepository refunds;
    private final BusinessCalendar calendar;
    private final RefundAuthorizedOutbox outbox;
    private final IdGenerator ids;
    private final Clock clock;

    public FinancialDecisionOpening(FinancialDecisionRepository decisions, RefundAuthorizationRepository refunds, BusinessCalendar calendar,
            RefundAuthorizedOutbox outbox, IdGenerator ids, Clock clock) {
        this.decisions = decisions;
        this.refunds = refunds;
        this.calendar = calendar;
        this.outbox = outbox;
        this.ids = ids;
        this.clock = clock;
    }

    // 013: the customer cancellation, with the base amount and the penalty it fixed.
    @Override
    public void open(UUID tenantId, UUID reservationId, Cents base, Cents penalty, Instant recognizedAt) {
        recognize(tenantId, reservationId, DecisionKind.CUSTOMER_CANCELLATION, base, penalty, recognizedAt);
    }

    // 012: the excess of a modification.
    @Override
    public void open(UUID tenantId, UUID reservationId, Cents excess, Instant recognizedAt) {
        recognize(tenantId, reservationId, DecisionKind.MODIFICATION_EXCESS, excess, null, recognizedAt);
    }

    @Override
    public void open(UUID tenantId, UUID reservationId, Cents excess) {
        recognize(tenantId, reservationId, DecisionKind.MODIFICATION_EXCESS, excess, null, Instant.now(clock));
    }

    // 010 and 011: a value that arrives after a cancellation, or the validated value of a cancellation for non-payment.
    @Override
    public void record(UUID tenantId, UUID reservationId, Cents value, Origin origin) {
        recognize(tenantId, reservationId, DecisionKind.PENDING_TREATMENT, value, null, Instant.now(clock));
    }

    // HU-COM-020 AC-20.7: the customer cancellation opened frozen at the instant of the request, with its 24 hours intact.
    @Override
    public void openFrozen(UUID tenantId, UUID reservationId, Cents base, Cents penalty, Instant recognizedAt) {
        recognize(tenantId, reservationId, DecisionKind.CUSTOMER_CANCELLATION, base, penalty, recognizedAt, true);
    }

    private void recognize(UUID tenantId, UUID reservationId, DecisionKind kind, Cents value, Cents penalty, Instant recognizedAt) {
        recognize(tenantId, reservationId, kind, value, penalty, recognizedAt, false);
    }

    private void recognize(UUID tenantId, UUID reservationId, DecisionKind kind, Cents value, Cents penalty, Instant recognizedAt, boolean frozen) {
        Optional<FinancialDecision> latest = decisions.findLatest(tenantId, reservationId, kind);
        if (latest.isEmpty()) {
            FinancialDecision opened = FinancialDecision.open(ids.newId(), tenantId, reservationId, kind, value, penalty, null, recognizedAt);
            decisions.save(frozen ? opened.freeze(recognizedAt) : opened);
            return;
        }
        FinancialDecision decision = latest.get();
        if (!decision.isDecided()) {
            decisions.save(decision.withLateValue(value));
            return;
        }
        if (decision.outcome() != DecisionOutcome.REFUND_FULL)
            throw new BusinessRuleViolation("the late value of a decision that ended as " + decision.outcome() + " belongs to the credit balance (HU-COM-017)");
        Instant now = Instant.now(clock);
        RefundAuthorization increment = RefundAuthorization.increment(ids.newId(), decision, value, now,
                calendar.addBusinessDays(now, RefundAuthorizationRules.BUSINESS_DAYS));
        refunds.save(increment);
        outbox.append(increment);
    }
}
