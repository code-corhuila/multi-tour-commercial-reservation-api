package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The open deadline of a reservation is its latest one; the expiry reads all of them. HU-COM-009 (the first payment) and HU-COM-011
// (the initial row at creation, the extension) write the rows; a reservation with no row is not refused for expiry (G-DL).
public final class JdbcPaymentDeadlineReader implements PaymentDeadlineReader {
    private final JdbcTemplate jdbc;

    public JdbcPaymentDeadlineReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PaymentDeadline> openDeadline(UUID tenantId, UUID reservationId) {
        return rows(tenantId, reservationId, " LIMIT 1").stream().findFirst();
    }

    @Override
    public List<PaymentDeadline> openDeadlines(UUID tenantId, UUID reservationId) {
        return rows(tenantId, reservationId, "");
    }

    private List<PaymentDeadline> rows(UUID tenantId, UUID reservationId, String limit) {
        return jdbc.query("SELECT deadline_kind, due_at, extension_hours, extension_granted_at, extension_granted_by, frozen_at, "
                + "remaining_at_freeze_minutes, freeze_cause FROM commercial_reservation.payment_deadline WHERE tenant_id = ? "
                + "AND reservation_id = ? ORDER BY due_at DESC" + limit, (row, index) -> new PaymentDeadline(
                        DeadlineKind.valueOf(row.getString("deadline_kind")), instant(row, "due_at"), (Integer) row.getObject("extension_hours"),
                        instant(row, "extension_granted_at"), row.getObject("extension_granted_by", UUID.class), instant(row, "frozen_at"),
                        (Integer) row.getObject("remaining_at_freeze_minutes"), row.getString("freeze_cause") == null ? null : co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause.valueOf(row.getString("freeze_cause"))), tenantId, reservationId);
    }

    private static Instant instant(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
