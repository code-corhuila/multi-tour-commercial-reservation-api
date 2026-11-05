package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.util.UUID;

// HU-COM-016 AC-16.13, AC-16.14, DP-04: the Administrator and the Collaborator read; the lists of job 2 take the tenant of the path.
public interface ReadRefunds {
    RefundPage<FinancialDecision> decisions(PaymentActor actor, DecisionKind kind, boolean pending, boolean attention, int page, int limit);

    FinancialDecision decision(PaymentActor actor, UUID financialDecisionId);

    RefundPage<RefundAuthorization> refunds(PaymentActor actor, ExecutionState state, boolean overdue, UUID financialDecisionId, int page, int limit);

    RefundAuthorization refund(PaymentActor actor, UUID refundAuthorizationId);

    RefundPage<FinancialDecision> dueDecisions(UUID tenantId, int page, int limit);

    RefundPage<RefundAuthorization> dueRefunds(UUID tenantId, int page, int limit);
}
