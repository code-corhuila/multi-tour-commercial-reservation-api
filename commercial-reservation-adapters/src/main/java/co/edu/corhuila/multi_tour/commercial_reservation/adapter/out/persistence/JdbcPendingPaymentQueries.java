package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PendingPaymentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PendingPaymentRow;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingPaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// AC-11.6, AC-11.11: the initial deadline of a reservation pending payment and the balance deadlines of a confirmed one with a balance, never
// a frozen deadline. The effective deadline is the lower of the due instant plus the extension and the departure start, derived here and
// never stored; the customer name is read for the screen only. The departure is read through the local mirror of the capacity row.
public final class JdbcPendingPaymentQueries implements PendingPaymentQueries {
    private static final String FROM = " FROM commercial_reservation.reservation r JOIN commercial_reservation.payment_deadline d ON "
            + "d.tenant_id = r.tenant_id AND d.reservation_id = r.reservation_id JOIN commercial_reservation.customer c ON "
            + "c.tenant_id = r.tenant_id AND c.customer_id = r.customer_id LEFT JOIN commercial_reservation.salida_capacity s ON "
            + "s.tenant_id = r.tenant_id AND s.salida_id = r.salida_id WHERE r.tenant_id = ? AND d.frozen_at IS NULL "
            + "AND (?::text IS NULL OR d.deadline_kind = ?::text) AND ((d.deadline_kind = 'INITIAL_PAYMENT' AND r.reservation_status = "
            + "'PENDING_PAYMENT') OR (d.deadline_kind <> 'INITIAL_PAYMENT' AND r.reservation_status = 'CONFIRMED' AND r.pending_balance_cents > 0))";
    private final JdbcTemplate jdbc;

    public JdbcPendingPaymentQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PendingPaymentPage page(UUID tenantId, DeadlineKind kind, int page, int limit) {
        String code = kind == null ? null : kind.name();
        long total = jdbc.queryForObject("SELECT count(*)" + FROM, Long.class, tenantId, code, code);
        var rows = jdbc.query("SELECT r.reservation_id, c.full_name, r.payment_type, r.payment_method, r.final_value_cents - "
                + "r.pending_balance_cents AS validated, r.pending_balance_cents, r.payment_status, d.deadline_kind, "
                + "LEAST(d.due_at + make_interval(hours => COALESCE(d.extension_hours, 0)), s.hora_inicio) AS effective, EXISTS (SELECT 1 FROM "
                + "commercial_reservation.payment_attempt a WHERE a.tenant_id = r.tenant_id AND a.reservation_id = r.reservation_id AND "
                + "a.validation_status = 'IN_VALIDATION') AS proof" + FROM + " ORDER BY effective, r.reservation_id LIMIT ? OFFSET ?",
                (row, index) -> new PendingPaymentRow(row.getObject("reservation_id", UUID.class), row.getString("full_name"),
                        PaymentType.valueOf(row.getString("payment_type")), PaymentMethod.valueOf(row.getString("payment_method")),
                        row.getLong("validated"), row.getLong("pending_balance_cents"), PaymentStatus.valueOf(row.getString("payment_status")),
                        DeadlineKind.valueOf(row.getString("deadline_kind")), row.getObject("effective", OffsetDateTime.class).toInstant(),
                        row.getBoolean("proof")), tenantId, code, code, limit, (long) (page - 1) * limit);
        return new PendingPaymentPage(rows, total);
    }
}
