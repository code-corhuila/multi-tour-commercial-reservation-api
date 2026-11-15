package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Instant;
import java.util.UUID;

// HU-COM-016 AC-16.14: the paginated reads of one tenant, newest first. Pending: no outcome, or REFUND_FULL without refund; attention: pending and not frozen
// (DP-09); overdue: past the due instant and not executed. The due lists are for job 2, which asks and never decides.
public interface RefundQueries {
    RefundPage<FinancialDecision> decisions(UUID tenantId, DecisionKind kind, boolean pending, boolean attention, int page, int limit);

    RefundPage<FinancialDecision> dueDecisions(UUID tenantId, Instant now, int page, int limit);

    RefundPage<RefundAuthorization> refunds(UUID tenantId, ExecutionState state, boolean overdue, Instant now, UUID financialDecisionId, int page, int limit);

    RefundPage<RefundAuthorization> dueRefunds(UUID tenantId, Instant now, int page, int limit);
}
