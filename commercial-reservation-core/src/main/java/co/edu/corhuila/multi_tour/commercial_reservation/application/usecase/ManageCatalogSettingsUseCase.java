package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogSettings;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageCatalogSettings;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogSettingsRepository;
import java.time.Clock;
import java.util.UUID;

public class ManageCatalogSettingsUseCase implements ManageCatalogSettings {

    private final CatalogSettingsRepository repository;
    private final Clock clock;

    public ManageCatalogSettingsUseCase(CatalogSettingsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public CatalogSettings get(UUID tenantId) {
        return repository.find(tenantId).orElseGet(CatalogSettings::defaults);
    }

    @Override
    public CatalogSettings setCollaboratorEditing(UUID tenantId, UUID actorId, boolean enabled) {
        var current = repository.find(tenantId);
        if (current.isPresent() && current.get().collaboratorEditing() == enabled) return current.get();
        var changed = new CatalogSettings(enabled, clock.instant(), actorId);
        repository.save(tenantId, changed);
        return changed;
    }
}
