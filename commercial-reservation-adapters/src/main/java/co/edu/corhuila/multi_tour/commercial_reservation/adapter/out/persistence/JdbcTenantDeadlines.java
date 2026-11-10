package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.instant;
import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.time;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDeadlineStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-020 RN-02, RN-03, RN-06 over payment_deadline (freeze columns of HU-COM-011, freeze_cause of HU-COM-020): the open deadline rows of one tenant with the start of their departure and whether
// it was cancelled (the capacity mirror), and the write of a frozen or resumed row with its freeze pair and its cause together (the checks of the engine refuse one without the others). The decision is
// the pure PaymentDeadline.frozen / resumed, so only a row that changed is saved. Every query leads with the tenant.
public final class JdbcTenantDeadlines implements TenantDeadlineStore {
    private final JdbcTemplate jdbc;

    public JdbcTenantDeadlines(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TenantDeadline> openOf(UUID tenantId) {
        return jdbc.query("SELECT d.payment_deadline_id, d.tenant_id, d.reservation_id, d.deadline_kind, d.due_at, d.extension_hours, d.extension_granted_at, d.extension_granted_by, "
                + "d.frozen_at, d.remaining_at_freeze_minutes, d.freeze_cause, c.hora_inicio, COALESCE(c.status = 'CANCELLED', false) AS departure_cancelled "
                + "FROM commercial_reservation.payment_deadline d JOIN commercial_reservation.reservation r ON r.tenant_id = d.tenant_id AND r.reservation_id = d.reservation_id "
                + "LEFT JOIN commercial_reservation.salida_capacity c ON c.tenant_id = r.tenant_id AND c.salida_id = r.salida_id "
                + "WHERE d.tenant_id = ? AND r.reservation_status IN ('PENDING_PAYMENT', 'CONFIRMED', 'IN_EXECUTION') ORDER BY d.reservation_id, d.due_at",
                (row, index) -> map(row), tenantId);
    }

    @Override
    public void save(TenantDeadline row) {
        PaymentDeadline d = row.deadline();
        jdbc.update("UPDATE commercial_reservation.payment_deadline SET due_at = ?, frozen_at = ?, remaining_at_freeze_minutes = ?, freeze_cause = ? WHERE tenant_id = ? AND payment_deadline_id = ?",
                time(d.dueAt()), time(d.frozenAt()), d.remainingAtFreezeMinutes(), d.freezeCause() == null ? null : d.freezeCause().name(), row.tenantId(), row.rowId());
    }

    private static TenantDeadline map(ResultSet r) throws SQLException {
        String cause = r.getString("freeze_cause");
        PaymentDeadline deadline = new PaymentDeadline(DeadlineKind.valueOf(r.getString("deadline_kind")), instant(r, "due_at"), (Integer) r.getObject("extension_hours"),
                instant(r, "extension_granted_at"), r.getObject("extension_granted_by", UUID.class), instant(r, "frozen_at"), (Integer) r.getObject("remaining_at_freeze_minutes"),
                cause == null ? null : FreezeCause.valueOf(cause));
        return new TenantDeadline(r.getObject("payment_deadline_id", UUID.class), r.getObject("tenant_id", UUID.class), r.getObject("reservation_id", UUID.class), deadline,
                instant(r, "hora_inicio"), r.getBoolean("departure_cancelled"));
    }
}
