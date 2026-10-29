package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatusCopy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The copy of the tenant status over tenant_status (db spec 021). Used inside the transaction of the use case (plan D-02).
public final class JdbcTenantStatusStore implements TenantStatusStore {
    private final JdbcTemplate jdbc;

    public JdbcTenantStatusStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TenantStatusCopy> findForUpdate(UUID tenantId) {
        return jdbc.query("SELECT identifier, status, time_zone, country, last_event_occurred_at "
                + "FROM commercial_reservation.tenant_status WHERE tenant_id = ? FOR UPDATE",
                (row, index) -> new TenantStatusCopy(tenantId, row.getString("identifier"),
                        TenantStatus.valueOf(row.getString("status")), row.getString("time_zone"),
                        row.getString("country"), row.getTimestamp("last_event_occurred_at").toInstant()),
                tenantId).stream().findFirst();
    }

    @Override
    public boolean insertIfAbsent(TenantStatusCopy copy) {
        Timestamp now = Timestamp.from(Instant.now());
        return jdbc.update("INSERT INTO commercial_reservation.tenant_status (tenant_id, identifier, status, time_zone, "
                + "country, last_event_occurred_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (tenant_id) DO NOTHING", copy.tenantId(), copy.identifier(), copy.status().name(),
                copy.timeZone(), copy.country(), Timestamp.from(copy.lastEventAt()), now, now) == 1;
    }

    @Override
    public boolean moveForward(TenantStatusCopy copy) {
        Timestamp at = Timestamp.from(copy.lastEventAt());
        return jdbc.update("UPDATE commercial_reservation.tenant_status SET status = ?, time_zone = ?, country = ?, "
                + "last_event_occurred_at = ?, updated_at = ? WHERE tenant_id = ? AND last_event_occurred_at < ?",
                copy.status().name(), copy.timeZone(), copy.country(), at, Timestamp.from(Instant.now()),
                copy.tenantId(), at) == 1;
    }
}
