package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.FollowupPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Append and read only: the application user holds no UPDATE and no DELETE on the table (AC-D11.3). Every query leads with the tenant.
// The note is stored and read; it is never logged.
public final class JdbcPaymentFollowupRepository implements PaymentFollowupRepository {
    private final JdbcTemplate jdbc;

    public JdbcPaymentFollowupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(PaymentFollowup f) {
        jdbc.update("INSERT INTO commercial_reservation.payment_followup (payment_followup_id, tenant_id, reservation_id, contacted_by, "
                + "contacted_at, note) VALUES (?, ?, ?, ?, ?, ?)", f.followupId(), f.tenantId(), f.reservationId(), f.contactedBy(),
                OffsetDateTime.ofInstant(f.contactedAt(), ZoneOffset.UTC), f.note());
    }

    @Override
    public FollowupPage page(UUID tenantId, UUID reservationId, int page, int limit) {
        long total = jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.payment_followup WHERE tenant_id = ? AND reservation_id = ?",
                Long.class, tenantId, reservationId);
        var items = jdbc.query("SELECT payment_followup_id, contacted_by, contacted_at, note FROM commercial_reservation.payment_followup "
                + "WHERE tenant_id = ? AND reservation_id = ? ORDER BY contacted_at DESC, payment_followup_id LIMIT ? OFFSET ?",
                (row, index) -> new PaymentFollowup(row.getObject("payment_followup_id", UUID.class), tenantId, reservationId,
                        row.getObject("contacted_by", UUID.class), row.getObject("contacted_at", OffsetDateTime.class).toInstant(),
                        row.getString("note")), tenantId, reservationId, limit, (long) (page - 1) * limit);
        return new FollowupPage(items, total);
    }
}
