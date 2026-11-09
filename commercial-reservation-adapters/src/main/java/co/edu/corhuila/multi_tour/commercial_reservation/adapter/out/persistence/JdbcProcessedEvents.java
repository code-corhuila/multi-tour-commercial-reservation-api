package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The ids of the events already applied (RN-11). A plain insert on purpose: if two deliveries of one event race, the
// key refuses the second and its transaction rolls back its effect with it.
public final class JdbcProcessedEvents implements ProcessedEvents {
    private final JdbcTemplate jdbc;

    public JdbcProcessedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean wasProcessed(UUID eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM commercial_reservation.processed_event WHERE event_id = ?)",
                Boolean.class, eventId));
    }

    @Override
    public void record(UUID eventId) {
        jdbc.update("INSERT INTO commercial_reservation.processed_event (event_id, processed_at) VALUES (?, now())",
                eventId);
    }
}
