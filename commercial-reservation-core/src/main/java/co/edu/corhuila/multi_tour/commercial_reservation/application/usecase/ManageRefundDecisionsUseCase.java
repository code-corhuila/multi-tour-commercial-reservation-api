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
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// HU-COM-016 plan D-04, D-07, D-09: the aggregates decide, this class orders what surrounds them in one transaction (the decision or the refund and its
// event commit together). The clock and the calendar are ports; the caller never sends an instant or an amount.
public class ManageRefundDecisionsUseCase implements ManageRefundDecisions {

    private final FinancialDecisionRepository decisions;
    private final RefundAuthorizationRepository refunds;
    private final BusinessCalendar calendar;
    private final RefundAuthorizedOutbox outbox;
    private final IdGenerator ids;
    private final PaymentTransaction transaction;
    private final Clock clock;
    private final SuspensionReader suspensions;

    public ManageRefundDecisionsUseCase(FinancialDecisionRepository decisions, RefundAuthorizationRepository refunds, BusinessCalendar calendar,
            RefundAuthorizedOutbox outbox, IdGenerator ids, PaymentTransaction transaction, Clock clock) {
        this(decisions, refunds, calendar, outbox, ids, transaction, clock, tenantId -> Optional.empty());
    }

    // HU-COM-016 DP-16: the suspension reader tells whether the tenant is inactive (HU-COM-020).
    public ManageRefundDecisionsUseCase(FinancialDecisionRepository decisions, RefundAuthorizationRepository refunds, BusinessCalendar calendar,
            RefundAuthorizedOutbox outbox, IdGenerator ids, PaymentTransaction transaction, Clock clock, SuspensionReader suspensions) {
        this.suspensions = suspensions;
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
        requireActiveTenant(actor.tenantId());
        FinancialDecision decision = load(actor.tenantId(), financialDecisionId);
        Instant now = Instant.now(clock);
        return transaction.inTransaction(() -> {
            FinancialDecision decided = decision.recordRefundFull(actor.actorId(), now);
            decisions.save(decided);
            return decided;
        });
    }

    @Override
    public RefundAuthorization authorize(PaymentActor actor, UUID financialDecisionId, String reason) {
        RefundAuthorizationRules.requireAdministrator(actor);
        requireActiveTenant(actor.tenantId());
        FinancialDecision decision = load(actor.tenantId(), financialDecisionId);
        if (refunds.findByDecision(actor.tenantId(), financialDecisionId).isPresent())
            throw new InvalidStatusTransitionException("the decision already has its refund authorized");
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
        requireActiveTenant(actor.tenantId());
        FinancialDecision decision = load(actor.tenantId(), financialDecisionId);
        Instant now = Instant.now(clock);
        return transaction.inTransaction(() -> {
            FinancialDecision rejected = decision.reject(actor.actorId(), justification, now);
            decisions.save(rejected);
            return rejected;
        });
    }

    // HU-COM-016 DP-16 (Project Lead, 2026-11-12): with the tenant inactive the decisions of 24 hours are closed; reading and executing refunds already determined stay open.
    private void requireActiveTenant(UUID tenantId) {
        if (suspensions.openSince(tenantId).isPresent()) throw new BusinessRuleViolation("the tenant is inactive: refund decisions are closed until it is active again");
    }

    private FinancialDecision load(UUID tenantId, UUID id) {
        return decisions.find(tenantId, id).orElseThrow(() -> new RefundNotFoundException("financial decision", id));
    }
}
