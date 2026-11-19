package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReservationReader;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Spec 023: the alive reservations of one departure of one tenant, by keyset on the reservation id. Pending payment or confirmed only: a cancelled, completed or in-execution
// reservation has no episode to open. The tenant leads the query, so the reservations of another tenant are never read.
public final class JdbcDepartureReservationReader implements DepartureReservationReader {
    private final JdbcTemplate jdbc;

    public JdbcDepartureReservationReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> findAliveByDeparture(UUID tenantId, UUID departureId, UUID afterReservationId, int limit) {
        return jdbc.query("SELECT reservation_id FROM commercial_reservation.reservation WHERE tenant_id = ? AND salida_id = ? "
                + "AND reservation_status IN ('PENDING_PAYMENT', 'CONFIRMED') AND (?::uuid IS NULL OR reservation_id > ?::uuid) ORDER BY reservation_id LIMIT ?",
                (row, index) -> row.getObject("reservation_id", UUID.class), tenantId, departureId, afterReservationId, afterReservationId, limit);
    }
}
