package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadRefunds;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// HU-COM-016 AC-16.13, AC-16.14, RNF-07: the role is judged before any query and the instant comes from the clock port.
public class ReadRefundsUseCase implements ReadRefunds {
    private final RefundQueries queries;
    private final FinancialDecisionRepository decisions;
    private final RefundAuthorizationRepository refunds;
    private final Clock clock;

    public ReadRefundsUseCase(RefundQueries queries, FinancialDecisionRepository decisions, RefundAuthorizationRepository refunds, Clock clock) {
        this.queries = queries;
        this.decisions = decisions;
        this.refunds = refunds;
        this.clock = clock;
    }

    @Override
    public RefundPage<FinancialDecision> decisions(PaymentActor actor, DecisionKind kind, boolean pending, boolean attention, int page, int limit) {
        RefundAuthorizationRules.requireReader(actor);
        return queries.decisions(actor.tenantId(), kind, pending, attention, page, limit);
    }

    @Override
    public FinancialDecision decision(PaymentActor actor, UUID financialDecisionId) {
        RefundAuthorizationRules.requireReader(actor);
        return decisions.find(actor.tenantId(), financialDecisionId).orElseThrow(() -> new RefundNotFoundException("financial decision", financialDecisionId));
    }

    @Override
    public RefundPage<RefundAuthorization> refunds(PaymentActor actor, ExecutionState state, boolean overdue, UUID financialDecisionId, int page, int limit) {
        RefundAuthorizationRules.requireReader(actor);
        return queries.refunds(actor.tenantId(), state, overdue, Instant.now(clock), financialDecisionId, page, limit);
    }

    @Override
    public RefundAuthorization refund(PaymentActor actor, UUID refundAuthorizationId) {
        RefundAuthorizationRules.requireReader(actor);
        return refunds.find(actor.tenantId(), refundAuthorizationId).orElseThrow(() -> new RefundNotFoundException("refund authorization", refundAuthorizationId));
    }

    @Override
    public RefundPage<FinancialDecision> dueDecisions(UUID tenantId, int page, int limit) {
        return queries.dueDecisions(tenantId, Instant.now(clock), page, limit);
    }

    @Override
    public RefundPage<RefundAuthorization> dueRefunds(UUID tenantId, int page, int limit) {
        return queries.dueRefunds(tenantId, Instant.now(clock), page, limit);
    }
}
