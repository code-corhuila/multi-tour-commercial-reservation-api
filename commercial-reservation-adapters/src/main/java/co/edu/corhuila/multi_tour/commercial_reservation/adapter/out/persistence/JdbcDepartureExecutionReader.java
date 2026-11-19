package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureExecutionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The status of the departure is the one of its mirror row (salida_capacity.status, English codes) and the reservations are the ones of the table reservation.
// The tenant is a condition of both statements, so a departure of another tenant is the same empty answer as a missing one. No personal column is read.
public final class JdbcDepartureExecutionReader implements DepartureExecutionReader {
    private final JdbcTemplate jdbc;

    public JdbcDepartureExecutionReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DepartureStatus> statusOf(UUID tenantId, UUID salidaId) {
        return jdbc.query("SELECT status FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?",
                (row, index) -> DepartureStatus.valueOf(row.getString("status")), tenantId, salidaId).stream().findFirst();
    }

    @Override
    public List<UUID> reservationIds(UUID tenantId, UUID salidaId) {
        return jdbc.query("SELECT reservation_id FROM commercial_reservation.reservation WHERE tenant_id = ? AND salida_id = ? ORDER BY created_at, reservation_id",
                (row, index) -> row.getObject("reservation_id", UUID.class), tenantId, salidaId);
    }
}
