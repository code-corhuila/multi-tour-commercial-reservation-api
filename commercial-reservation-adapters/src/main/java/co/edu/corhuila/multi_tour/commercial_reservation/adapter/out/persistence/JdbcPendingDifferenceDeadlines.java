package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingDifferenceDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-012 plan D-06: the advance hours of the balance deadline come from the payment condition (the row of the service before the row of
// the tenant); the pending-difference row is unique per reservation, so a second one is kept out and the first stays (L6).
public final class JdbcPendingDifferenceDeadlines implements PendingDifferenceDeadlines {
    private final JdbcTemplate jdbc;

    public JdbcPendingDifferenceDeadlines(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OptionalInt balanceAdvanceHours(UUID tenantId, UUID serviceId, PaymentType type) {
        return jdbc.query("SELECT balance_deadline_advance_hours FROM commercial_reservation.payment_condition WHERE tenant_id = ? "
                + "AND payment_type = ? AND (service_id = ? OR service_id IS NULL) ORDER BY service_id IS NULL LIMIT 1", (row, index) -> {
                    int hours = row.getInt(1);
                    return row.wasNull() ? OptionalInt.empty() : OptionalInt.of(hours);
                }, tenantId, type.name(), serviceId).stream().findFirst().orElse(OptionalInt.empty());
    }

    @Override
    public void record(UUID tenantId, UUID reservationId, Instant dueAt) {
        jdbc.update("INSERT INTO commercial_reservation.payment_deadline (payment_deadline_id, tenant_id, reservation_id, deadline_kind, due_at) "
                + "VALUES (?, ?, ?, 'PENDING_DIFFERENCE', ?) ON CONFLICT DO NOTHING", UUID.randomUUID(), tenantId, reservationId,
                OffsetDateTime.ofInstant(dueAt, ZoneOffset.UTC));
    }
}
