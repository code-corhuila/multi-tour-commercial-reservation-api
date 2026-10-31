package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogSettings;
import java.util.Optional;
import java.util.UUID;

public interface CatalogSettingsRepository {
    /** The settings stored for the tenant; empty when nobody ever set them. */
    Optional<CatalogSettings> find(UUID tenantId);

    /** Stores the settings of the tenant, replacing what it had: one row per tenant. */
    void save(UUID tenantId, CatalogSettings settings);
}
