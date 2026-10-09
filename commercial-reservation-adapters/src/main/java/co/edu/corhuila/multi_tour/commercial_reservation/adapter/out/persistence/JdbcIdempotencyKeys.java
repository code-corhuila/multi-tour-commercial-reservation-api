package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcIdempotencyKeys {
    private final JdbcTemplate jdbc;

    public JdbcIdempotencyKeys(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tryRegister(UUID tenantId, String key, String resourceType, UUID resourceId) {
        return jdbc.update("INSERT INTO commercial_reservation.idempotency_key "
                + "(tenant_id, key, resource_type, resource_id) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                tenantId, key, resourceType, resourceId) == 1;
    }

    public Optional<UUID> findResourceId(UUID tenantId, String key) {
        return jdbc.query("SELECT resource_id FROM commercial_reservation.idempotency_key "
                + "WHERE tenant_id = ? AND key = ?", (row, index) -> row.getObject("resource_id", UUID.class),
                tenantId, key).stream().findFirst();
    }
}
