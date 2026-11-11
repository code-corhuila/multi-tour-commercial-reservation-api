package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueDeadlinePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DuePaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// AC-11.2, AC-11.11: the effective instant (the lower of the due instant plus the extension and the departure start, derived here) at or
// before now, never a frozen deadline, and only a reservation that can still expire: pending payment for the initial kind, confirmed
// with a balance for the others. The job asks the service to expire each; the service decides again.
public final class JdbcDuePaymentQueries implements DuePaymentQueries {
    private static final String EFFECTIVE = "LEAST(d.due_at + make_interval(hours => COALESCE(d.extension_hours, 0)), s.hora_inicio)";
    private static final String FROM = " FROM commercial_reservation.payment_deadline d JOIN commercial_reservation.reservation r ON "
            + "r.tenant_id = d.tenant_id AND r.reservation_id = d.reservation_id LEFT JOIN commercial_reservation.salida_capacity s ON "
            + "s.tenant_id = r.tenant_id AND s.salida_id = r.salida_id WHERE d.tenant_id = ? AND d.frozen_at IS NULL AND " + EFFECTIVE
            + " <= ? AND ((d.deadline_kind = 'INITIAL_PAYMENT' AND r.reservation_status = 'PENDING_PAYMENT') OR (d.deadline_kind <> "
            + "'INITIAL_PAYMENT' AND r.reservation_status = 'CONFIRMED' AND r.pending_balance_cents > 0))";
    private final JdbcTemplate jdbc;

    public JdbcDuePaymentQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DueDeadlinePage page(UUID tenantId, Instant now, int page, int limit) {
        OffsetDateTime at = OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC);
        long total = jdbc.queryForObject("SELECT count(*)" + FROM, Long.class, tenantId, at);
        var rows = jdbc.query("SELECT d.reservation_id, d.deadline_kind, d.due_at, d.extension_hours, d.extension_granted_at, "
                + "d.extension_granted_by, s.hora_inicio, " + EFFECTIVE + " AS effective" + FROM
                + " ORDER BY effective, d.reservation_id LIMIT ? OFFSET ?", (row, index) -> new DeadlineView(
                        row.getObject("reservation_id", UUID.class), new PaymentDeadline(DeadlineKind.valueOf(row.getString("deadline_kind")),
                                row.getObject("due_at", OffsetDateTime.class).toInstant(), (Integer) row.getObject("extension_hours"),
                                instant(row.getObject("extension_granted_at", OffsetDateTime.class)), row.getObject("extension_granted_by", UUID.class),
                                null, null), instant(row.getObject("hora_inicio", OffsetDateTime.class)),
                        row.getObject("effective", OffsetDateTime.class).toInstant()), tenantId, at, limit, (long) (page - 1) * limit);
        return new DueDeadlinePage(rows, total);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
