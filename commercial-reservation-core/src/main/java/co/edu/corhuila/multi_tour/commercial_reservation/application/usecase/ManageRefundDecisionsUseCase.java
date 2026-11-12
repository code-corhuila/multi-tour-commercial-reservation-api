package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageRefundDecisions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.BusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// HU-COM-016 plan D-04, D-07, D-09: the aggregates decide, this class orders what surrounds them in one transaction (the decision or the refund and its
// event commit together). The clock and the calendar are ports; the caller never sends an instant or an amount.
public class ManageRefundDecisionsUseCase implements ManageRefundDecisions {

    private final FinancialDecisionRepository decisions;
    private final RefundAuthorizationRepository refunds;
    private final BusinessCalendar calendar;
    private final RefundAuthorizedOutbox outbox;
    private static final String DEFAULT_REASON = "Refund of 100 % decided by the Tenant Administrator";

    private final IdGenerator ids;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public ManageRefundDecisionsUseCase(FinancialDecisionRepository decisions, RefundAuthorizationRepository refunds, BusinessCalendar calendar,
            RefundAuthorizedOutbox outbox, IdGenerator ids, PaymentTransaction transaction, Clock clock) {
        this.decisions = decisions;
        this.refunds = refunds;
        this.calendar = calendar;
        this.outbox = outbox;
        this.ids = ids;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public FinancialDecision recordRefundDecision(PaymentActor actor, UUID financialDecisionId, String reason) {
        RefundAuthorizationRules.requireAdministrator(actor);
        FinancialDecision decision = load(actor.tenantId(), financialDecisionId);
        Instant now = Instant.now(clock);
        return transaction.inTransaction(() -> {
            FinancialDecision decided = decision.recordRefundFull(actor.actorId(), now);
            decisions.save(decided);
            // HU-COM-016 DP-01 (Project Lead, 2026-11-12): the decision of the 100 % refund is also its authorization, in this same transaction; DP-02: the five days count from the determination.
            RefundAuthorization refund = RefundAuthorization.authorize(ids.newId(), decided, reason == null || reason.isBlank() ? DEFAULT_REASON : reason, actor.actorId(), now,
                    calendar.addBusinessDays(decided.recognizedAt(), RefundAuthorizationRules.BUSINESS_DAYS));
            refunds.save(refund);
            outbox.append(refund);
            return decided;
        });
    }

    @Override
    public RefundAuthorization authorize(PaymentActor actor, UUID financialDecisionId, String reason) {
        RefundAuthorizationRules.requireAdministrator(actor);
        FinancialDecision decision = load(actor.tenantId(), financialDecisionId);
        // DP-01: idempotent by state. A decision that already has its refund (created by the decision itself) answers that refund; this route serves old clients and rows decided before.
        var existing = refunds.findByDecision(actor.tenantId(), financialDecisionId);
        if (existing.isPresent()) return existing.get();
        Instant now = Instant.now(clock);
        return transaction.inTransaction(() -> {
            // HU-COM-016 DP-02: the five business days count from the determination (recognizedAt), not from this authorization; a resumed decision keeps its recognition instant (question for the Project Lead).
            RefundAuthorization refund = RefundAuthorization.authorize(ids.newId(), decision, reason, actor.actorId(), now,
                    calendar.addBusinessDays(decision.recognizedAt(), RefundAuthorizationRules.BUSINESS_DAYS));
            refunds.save(refund);
            outbox.append(refund);
            return refund;
        });
    }

    @Override
    public FinancialDecision reject(PaymentActor actor, UUID financialDecisionId, String justification) {
        RefundAuthorizationRules.requireAdministrator(actor);
        FinancialDecision decision = load(actor.tenantId(), financialDecisionId);
        Instant now = Instant.now(clock);
        return transaction.inTransaction(() -> {
            FinancialDecision rejected = decision.reject(actor.actorId(), justification, now);
            decisions.save(rejected);
            return rejected;
        });
    }

    private FinancialDecision load(UUID tenantId, UUID id) {
        return decisions.find(tenantId, id).orElseThrow(() -> new RefundNotFoundException("financial decision", id));
    }
}
