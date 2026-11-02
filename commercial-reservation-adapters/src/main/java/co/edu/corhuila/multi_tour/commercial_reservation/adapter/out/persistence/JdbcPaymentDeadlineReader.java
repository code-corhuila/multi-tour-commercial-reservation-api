package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The open deadline of a reservation is its latest one. Nothing in this service writes the rows yet (HU-COM-011 owns them), so a
// reservation with no row is not refused for expiry (G-DL).
public final class JdbcPaymentDeadlineReader implements PaymentDeadlineReader {
    private final JdbcTemplate jdbc;

    public JdbcPaymentDeadlineReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PaymentDeadline> openDeadline(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT deadline_kind, due_at FROM commercial_reservation.payment_deadline WHERE tenant_id = ? "
                + "AND reservation_id = ? ORDER BY due_at DESC LIMIT 1", (row, index) -> new PaymentDeadline(
                        DeadlineKind.valueOf(row.getString("deadline_kind")), row.getObject("due_at", OffsetDateTime.class).toInstant()),
                tenantId, reservationId).stream().findFirst();
    }
}
