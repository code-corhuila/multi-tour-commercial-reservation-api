package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The capacity row of a departure (salida_capacity), keyed by (tenant, salida). The status codes are stored as the
// enum names. The mirror never writes held_seats: only reservations move them, so a concurrent hold is never overwritten.
// last_event_occurred_at is the occurredAt of the last event applied (DP-16, DD-8); a row born before it reads null.
public final class JdbcDepartureStateRepository implements DepartureStateRepository {
    static final String SELECT = "SELECT salida_id, tenant_id, service_id, hora_inicio, status, effective_capacity, "
            + "held_seats, last_event_occurred_at FROM commercial_reservation.salida_capacity";

    private final JdbcTemplate jdbc;

    public JdbcDepartureStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DepartureState> find(UUID tenantId, UUID departureId) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? AND salida_id = ?", JdbcDepartureStateRepository::restore,
                tenantId, departureId).stream().findFirst();
    }

    @Override
    public void save(DepartureState state) {
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, "
                        + "service_id, hora_inicio, status, effective_capacity, held_seats, last_event_occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, salida_id) DO UPDATE SET hora_inicio = EXCLUDED.hora_inicio, "
                        + "status = EXCLUDED.status, effective_capacity = EXCLUDED.effective_capacity, "
                        + "last_event_occurred_at = EXCLUDED.last_event_occurred_at",
                UUID.randomUUID(), state.tenantId(), state.departureId(), state.serviceId(),
                state.startsAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC), state.status().name(),
                state.effectiveCapacity(), state.heldSeats(), marker(state));
    }

    private static OffsetDateTime marker(DepartureState state) {
        return state.lastEventOccurredAt() == null ? null
                : state.lastEventOccurredAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    static DepartureState restore(ResultSet row, int index) throws SQLException {
        return new DepartureState(row.getObject("salida_id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getObject("service_id", UUID.class), row.getObject("hora_inicio", OffsetDateTime.class).toInstant(),
                DepartureStatus.valueOf(row.getString("status")), row.getInt("effective_capacity"),
                row.getInt("held_seats"), instant(row.getObject("last_event_occurred_at", OffsetDateTime.class)));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
