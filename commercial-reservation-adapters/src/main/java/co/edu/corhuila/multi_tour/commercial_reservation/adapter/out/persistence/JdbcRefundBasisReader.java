package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundBasis;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundBasisReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Spec 026: what the refund determination needs of a reservation, in one read led by the tenant. The validated value is the final value minus the pending
// balance (values still in validation are not part of it, RN-RES-006A); the start of the departure comes from the mirror row and is null when there is none.
public final class JdbcRefundBasisReader implements RefundBasisReader {
    private final JdbcTemplate jdbc;

    public JdbcRefundBasisReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RefundBasis> find(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT r.party_size, r.service_id, r.final_value_cents - r.pending_balance_cents AS validated, "
                + "(SELECT d.hora_inicio FROM commercial_reservation.salida_capacity d WHERE d.tenant_id = r.tenant_id AND d.salida_id = r.salida_id LIMIT 1) AS starts_at "
                + "FROM commercial_reservation.reservation r WHERE r.tenant_id = ? AND r.reservation_id = ?",
                (row, index) -> {
                    OffsetDateTime start = row.getObject("starts_at", OffsetDateTime.class);
                    return new RefundBasis(reservationId, new Cents(Math.max(0, row.getLong("validated"))), row.getInt("party_size"),
                            row.getObject("service_id", UUID.class), start == null ? null : start.toInstant());
                }, tenantId, reservationId).stream().findFirst();
    }
}
