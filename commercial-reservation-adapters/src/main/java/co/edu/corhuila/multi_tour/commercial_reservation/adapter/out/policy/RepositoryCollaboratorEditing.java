package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.policy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogSettings;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogSettingsRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CollaboratorEditingPolicy;
import java.util.UUID;

// The flag is business configuration over the catalog (RF-004, decision of 2026-10-30): the row of the tenant says it,
// and a tenant without a row has it off.
public final class RepositoryCollaboratorEditing implements CollaboratorEditingPolicy {
    private final CatalogSettingsRepository settings;

    public RepositoryCollaboratorEditing(CatalogSettingsRepository settings) {
        this.settings = settings;
    }

    @Override
    public boolean allowsDescriptiveUpdates(UUID tenantId) {
        return settings.find(tenantId).map(CatalogSettings::collaboratorEditing).orElse(false);
    }
}
