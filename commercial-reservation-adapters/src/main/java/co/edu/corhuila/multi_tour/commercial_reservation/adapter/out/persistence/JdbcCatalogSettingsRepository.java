package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogSettings;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogSettingsRepository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcCatalogSettingsRepository implements CatalogSettingsRepository {
    private final JdbcTemplate jdbc;

    public JdbcCatalogSettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CatalogSettings> find(UUID tenantId) {
        return jdbc.query("SELECT collaborator_editing, updated_by, updated_at FROM commercial_reservation.tenant_catalog_settings "
                        + "WHERE tenant_id = ?",
                (row, index) -> new CatalogSettings(row.getBoolean("collaborator_editing"),
                        row.getTimestamp("updated_at").toInstant(), row.getObject("updated_by", UUID.class)),
                tenantId).stream().findFirst();
    }

    @Override
    public void save(UUID tenantId, CatalogSettings settings) {
        var at = Timestamp.from(settings.updatedAt());
        // One row per tenant: the first save creates it, the next ones replace the flag, the author and the instant.
        jdbc.update("INSERT INTO commercial_reservation.tenant_catalog_settings "
                        + "(tenant_id, collaborator_editing, updated_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id) DO UPDATE SET collaborator_editing = EXCLUDED.collaborator_editing, "
                        + "updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at",
                tenantId, settings.collaboratorEditing(), settings.updatedBy(), at, at);
    }
}
