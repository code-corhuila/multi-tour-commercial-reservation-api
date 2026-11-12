package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

// HU-COM-016 AC-16.14, RNF-01: the reads over the tables and indexes of the db. Every query leads with the tenant, the order is stable and pending, attention
// and overdue are derived here, never stored (DP-09).
public final class JdbcRefundQueries implements RefundQueries {
    private static final String DECISIONS = " FROM commercial_reservation.financial_decision d WHERE d.tenant_id = ?";
    private static final String REFUNDS = " FROM commercial_reservation.refund_authorization r WHERE r.tenant_id = ?";
    private static final String PENDING = " AND (d.outcome IS NULL OR (d.outcome = 'REFUND_FULL' AND NOT EXISTS (SELECT 1 FROM "
            + "commercial_reservation.refund_authorization x WHERE x.tenant_id = d.tenant_id AND x.financial_decision_id = d.financial_decision_id)))";
    private final JdbcTemplate jdbc;

    public JdbcRefundQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RefundPage<FinancialDecision> decisions(UUID tenantId, DecisionKind kind, boolean pending, boolean attention, int page, int limit) {
        List<Object> args = new ArrayList<>(List.of(tenantId));
        StringBuilder where = new StringBuilder(DECISIONS);
        if (kind != null) { where.append(" AND d.decision_kind = ?"); args.add(kind.name()); }
        if (pending || attention) where.append(PENDING);
        if (attention) where.append(" AND d.frozen_at IS NULL");
        return page("d.recognized_at DESC, d.financial_decision_id DESC", where.toString(), args, page, limit, "d.", JdbcFinancialDecisionRepository.COLUMNS,
                (row, index) -> JdbcFinancialDecisionRepository.map(row));
    }

    @Override
    public RefundPage<FinancialDecision> dueDecisions(UUID tenantId, Instant now, int page, int limit) {
        List<Object> args = List.of(tenantId, time(now));
        return page("d.recognized_at, d.financial_decision_id", DECISIONS + " AND d.outcome IS NULL AND d.frozen_at IS NULL AND COALESCE(d.resumed_due_at, d.recognized_at + interval '24 hours') <= ?", args,
                page, limit, "d.", JdbcFinancialDecisionRepository.COLUMNS, (row, index) -> JdbcFinancialDecisionRepository.map(row));
    }

    @Override
    public RefundPage<RefundAuthorization> refunds(UUID tenantId, ExecutionState state, boolean overdue, Instant now, UUID financialDecisionId, int page,
            int limit) {
        List<Object> args = new ArrayList<>(List.of(tenantId));
        StringBuilder where = new StringBuilder(REFUNDS);
        if (state != null) { where.append(" AND r.execution_state = ?"); args.add(state.name()); }
        if (overdue) { where.append(" AND r.execution_state <> 'EXECUTED' AND r.execution_due_at <= ?"); args.add(time(now)); }
        if (financialDecisionId != null) { where.append(" AND r.financial_decision_id = ?"); args.add(financialDecisionId); }
        return page("r.authorized_at DESC, r.refund_authorization_id DESC", where.toString(), args, page, limit, "r.", JdbcRefundAuthorizationRepository.COLUMNS,
                (row, index) -> JdbcRefundAuthorizationRepository.map(row));
    }

    @Override
    public RefundPage<RefundAuthorization> dueRefunds(UUID tenantId, Instant now, int page, int limit) {
        List<Object> args = List.of(tenantId, time(now));
        return page("r.execution_due_at, r.refund_authorization_id", REFUNDS + " AND r.execution_state <> 'EXECUTED' AND r.overdue_marked_at IS NULL "
                + "AND r.execution_due_at <= ?", args, page, limit, "r.", JdbcRefundAuthorizationRepository.COLUMNS,
                (row, index) -> JdbcRefundAuthorizationRepository.map(row));
    }

    private <T> RefundPage<T> page(String order, String from, List<Object> args, int page, int limit, String alias, String columns, RowMapper<T> mapper) {
        long total = jdbc.queryForObject("SELECT count(*)" + from, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(limit);
        pageArgs.add((long) (page - 1) * limit);
        String select = "SELECT " + String.join(", ", java.util.Arrays.stream(columns.split(", ")).map(c -> alias + c).toList()) + from + " ORDER BY " + order
                + " LIMIT ? OFFSET ?";
        return new RefundPage<>(jdbc.query(select, mapper, pageArgs.toArray()), total);
    }

    private static OffsetDateTime time(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
