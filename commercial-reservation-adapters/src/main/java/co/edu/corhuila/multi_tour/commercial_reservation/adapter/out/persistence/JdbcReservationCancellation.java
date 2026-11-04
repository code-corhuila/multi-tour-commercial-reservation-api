package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationCancelled;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The cancellation that a decision after the deadline asks for (HU-COM-010 AC-10.20, AC-10.21), in the transaction of the decision: status,
// cause and instant in ONE update (chk_reservation_cancellation_coherent), so the engine accepts it, and the event with the seats released
// earlier in the same transaction. A reservation already cancelled matches nothing: no second event.
public final class JdbcReservationCancellation implements ReservationCancellation {
    private final JdbcTemplate jdbc;
    private final JdbcPaymentEventOutbox outbox;

    public JdbcReservationCancellation(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.outbox = new JdbcPaymentEventOutbox(jdbc);
    }

    @Override
    public void cancelForNonPayment(UUID tenantId, UUID reservationId, Instant now) {
        int updated = jdbc.update("UPDATE commercial_reservation.reservation SET reservation_status = 'CANCELLED', cancellation_cause = ?, "
                + "cancelled_at = ?, cancelled_by = NULL WHERE tenant_id = ? AND reservation_id = ? AND reservation_status IN "
                + "('PENDING_PAYMENT', 'CONFIRMED')", CancellationCause.PAYMENT_EXPIRY.name(), OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                tenantId, reservationId);
        if (updated == 0) return;
        int seats = jdbc.query("SELECT party_size FROM commercial_reservation.capacity_allocation WHERE tenant_id = ? AND reservation_id = ? "
                + "AND released_at IS NOT NULL ORDER BY released_at DESC LIMIT 1", (row, index) -> row.getInt("party_size"), tenantId,
                reservationId).stream().findFirst().orElse(0);
        outbox.append(new ReservationCancelled(tenantId, reservationId, CancellationCause.PAYMENT_EXPIRY, seats, now));
    }
}
