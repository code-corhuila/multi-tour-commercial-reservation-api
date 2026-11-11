package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogSettings;
import java.util.UUID;

public interface ManageCatalogSettings {

    CatalogSettings get(UUID tenantId);

    /** Idempotent: the same value again changes nothing, neither the author nor the instant. */
    CatalogSettings setCollaboratorEditing(UUID tenantId, UUID actorId, boolean enabled);
}
