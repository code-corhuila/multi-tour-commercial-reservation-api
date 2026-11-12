package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationClosingReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Closing;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcReservationClosingReader implements ReservationClosingReader {
    private final JdbcTemplate jdbc;

    public JdbcReservationClosingReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Closing> closingOf(UUID tenantId, UUID reservationId) {
        return Optional.empty();
    }
}
