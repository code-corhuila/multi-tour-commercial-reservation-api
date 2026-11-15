package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReprogrammingMarkReader;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-013 RN-07, AC-13.6 (declared debt DD-5, closed here): a reservation carries the mark "Pendiente de respuesta de reprogramacion" while it has
// an open rescheduling episode of HU-COM-018 (db table rescheduling_episode); the mark ends when the episode is accepted or cancelled. The query leads
// with the tenant, so a reservation of another tenant is never marked.
public final class JdbcReprogrammingMark implements ReprogrammingMarkReader {
    private final JdbcTemplate jdbc;

    public JdbcReprogrammingMark(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isMarked(UUID tenantId, UUID reservationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM commercial_reservation.rescheduling_episode "
                + "WHERE tenant_id = ? AND reservation_id = ? AND episode_state = 'OPEN')", Boolean.class, tenantId, reservationId));
    }
}
