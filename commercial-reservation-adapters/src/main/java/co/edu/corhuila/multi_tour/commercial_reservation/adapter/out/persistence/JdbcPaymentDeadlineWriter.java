package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineWriter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// RN-03: the three columns of the single extension are written together on the initial deadline of the tenant. A second write is
// refused by the engine (trg_payment_deadline_extension_guard); a row of another tenant matches nothing and is reported.
public final class JdbcPaymentDeadlineWriter implements PaymentDeadlineWriter {
    private final JdbcTemplate jdbc;

    public JdbcPaymentDeadlineWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveExtension(UUID tenantId, UUID reservationId, int hours, Instant grantedAt, UUID grantedBy) {
        int updated = jdbc.update("UPDATE commercial_reservation.payment_deadline SET extension_hours = ?, extension_granted_at = ?, "
                + "extension_granted_by = ? WHERE tenant_id = ? AND reservation_id = ? AND deadline_kind = 'INITIAL_PAYMENT'", hours,
                OffsetDateTime.ofInstant(grantedAt, ZoneOffset.UTC), grantedBy, tenantId, reservationId);
        if (updated != 1) throw new IllegalStateException("the initial deadline of the reservation was not found");
    }
}
