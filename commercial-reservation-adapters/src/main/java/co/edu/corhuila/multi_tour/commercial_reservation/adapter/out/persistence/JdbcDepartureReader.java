package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Scheduled departures that start after the instant the caller gives, ordered by start (plan D-07). The clock is the
// caller's, not the database's, so the use case and the query agree. Full departures are kept for the use case to drop.
public final class JdbcDepartureReader implements DepartureReader {
    private final JdbcTemplate jdbc;

    public JdbcDepartureReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DepartureState> findOpen(UUID tenantId, UUID serviceId, Instant now) {
        return jdbc.query(JdbcDepartureStateRepository.SELECT + " WHERE tenant_id = ? AND service_id = ? "
                        + "AND status = 'SCHEDULED' AND hora_inicio > ? ORDER BY hora_inicio, salida_id",
                JdbcDepartureStateRepository::restore, tenantId, serviceId, now.atOffset(ZoneOffset.UTC));
    }
}
