package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InitialDeadlineRecorder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// One initial deadline per reservation: the engine refuses a second one (uq_payment_deadline_initial_reservation).
public final class JdbcInitialDeadlineRecorder implements InitialDeadlineRecorder {
    private final JdbcTemplate jdbc;

    public JdbcInitialDeadlineRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID tenantId, UUID reservationId, Instant dueAt) {
        jdbc.update("INSERT INTO commercial_reservation.payment_deadline (payment_deadline_id, tenant_id, reservation_id, deadline_kind, due_at) "
                + "VALUES (?, ?, ?, 'INITIAL_PAYMENT', ?)", UUID.randomUUID(), tenantId, reservationId, OffsetDateTime.ofInstant(dueAt, ZoneOffset.UTC));
    }
}
