package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.InternalDepartureReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.InternalDepartureReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InternalDepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The tenant and the departure are conditions of every statement, so a departure of another tenant is the same empty answer as a missing one. Only the
// reservation table is read: no customer column, so no personal field can reach the answer. validated = final value less pending balance (RN-RES-006A).
public final class JdbcInternalDepartureReservationQueries implements InternalDepartureReservationQueries {
    private final JdbcTemplate jdbc;

    public JdbcInternalDepartureReservationQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<InternalDepartureReservationPage> ofDeparture(UUID tenantId, UUID salidaId, int page, int limit) {
        int known = jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?", Integer.class, tenantId, salidaId);
        if (known == 0) return Optional.empty();
        long total = jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.reservation WHERE tenant_id = ? AND salida_id = ?", Long.class, tenantId, salidaId);
        List<InternalDepartureReservation> rows = jdbc.query("SELECT reservation_id, reservation_status, party_size, customer_id, final_value_cents - pending_balance_cents AS validated "
                + "FROM commercial_reservation.reservation WHERE tenant_id = ? AND salida_id = ? ORDER BY created_at, reservation_id LIMIT ? OFFSET ?",
                (row, index) -> new InternalDepartureReservation(row.getObject("reservation_id", UUID.class), ReservationStatus.valueOf(row.getString("reservation_status")),
                        row.getInt("party_size"), row.getObject("customer_id", UUID.class), row.getLong("validated")), tenantId, salidaId, limit, (page - 1L) * limit);
        return Optional.of(new InternalDepartureReservationPage(rows, total));
    }
}
