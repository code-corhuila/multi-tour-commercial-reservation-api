package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.instant;
import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.time;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizationMode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.OutflowMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-016 plan D-03 of the db (-db 031, guard 007): one row per authorized refund and its execution, never deleted. A save updates only the execution
// columns and the overdue mark (the authorization columns are immutable in the engine) and inserts when the row does not exist yet. cash_movement_id is a
// logical reference with no key (Annex J.3.4). Every query leads with the tenant. The reason is stored and read, never logged.
public final class JdbcRefundAuthorizationRepository implements RefundAuthorizationRepository {
    static final String COLUMNS = "refund_authorization_id, tenant_id, reservation_id, refund_origin, financial_decision_id, "
            + "potential_refund_value_cents, authorized_amount_cents, reason, authorization_mode, authorized_by, authorized_at, execution_due_at, "
            + "execution_state, executing_at, executed_by, outflow_method, executed_at, cash_movement_id, overdue_marked_at";
    private final JdbcTemplate jdbc;

    public JdbcRefundAuthorizationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RefundAuthorization> find(UUID tenantId, UUID refundAuthorizationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM commercial_reservation.refund_authorization WHERE tenant_id = ? AND refund_authorization_id = ?",
                (row, index) -> map(row), tenantId, refundAuthorizationId).stream().findFirst();
    }

    @Override
    public Optional<RefundAuthorization> findByDecision(UUID tenantId, UUID financialDecisionId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM commercial_reservation.refund_authorization WHERE tenant_id = ? AND financial_decision_id = ? "
                + "ORDER BY authorized_at, refund_authorization_id LIMIT 1", (row, index) -> map(row), tenantId, financialDecisionId).stream().findFirst();
    }

    @Override
    public void save(RefundAuthorization r) {
        int updated = jdbc.update("UPDATE commercial_reservation.refund_authorization SET execution_state = ?, executing_at = ?, executed_by = ?, "
                + "outflow_method = ?, executed_at = ?, cash_movement_id = ?, overdue_marked_at = ? WHERE tenant_id = ? AND refund_authorization_id = ?",
                r.state().name(), time(r.executingAt()), r.executedBy(), r.outflowMethod() == null ? null : r.outflowMethod().name(), time(r.executedAt()),
                r.cashMovementId(), time(r.overdueMarkedAt()), r.tenantId(), r.id());
        if (updated == 0) {
            jdbc.update("INSERT INTO commercial_reservation.refund_authorization (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    r.id(), r.tenantId(), r.reservationId(), r.origin().name(), r.financialDecisionId(), r.potentialRefundValue().amount(),
                    r.authorizedAmount().amount(), r.reason(), r.mode().name(), r.authorizedBy(), time(r.authorizedAt()), time(r.executionDueAt()),
                    r.state().name(), time(r.executingAt()), r.executedBy(), r.outflowMethod() == null ? null : r.outflowMethod().name(),
                    time(r.executedAt()), r.cashMovementId(), time(r.overdueMarkedAt()));
        }
    }

    static RefundAuthorization map(ResultSet row) throws SQLException {
        String method = row.getString("outflow_method");
        return new RefundAuthorization(row.getObject("refund_authorization_id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getObject("reservation_id", UUID.class), RefundOrigin.valueOf(row.getString("refund_origin")),
                row.getObject("financial_decision_id", UUID.class), new Cents(row.getLong("potential_refund_value_cents")),
                new Cents(row.getLong("authorized_amount_cents")), row.getString("reason"), AuthorizationMode.valueOf(row.getString("authorization_mode")),
                row.getObject("authorized_by", UUID.class), instant(row, "authorized_at"), instant(row, "execution_due_at"),
                ExecutionState.valueOf(row.getString("execution_state")), instant(row, "executing_at"), row.getObject("executed_by", UUID.class),
                method == null ? null : OutflowMethod.valueOf(method), instant(row, "executed_at"), row.getObject("cash_movement_id", UUID.class),
                instant(row, "overdue_marked_at"));
    }
}
