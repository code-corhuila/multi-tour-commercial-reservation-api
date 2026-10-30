package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogSettings;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogSettingsRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-001 T-05 (RF-004, decision of 2026-10-30): the collaborator-editing flag is one value per tenant, off until the
// Tenant Administrator sets it. A tenant without a stored row has it off.
class ManageCatalogSettingsUseCaseTest extends UseCaseFixture {
    private final Map<UUID, CatalogSettings> stored = new HashMap<>();
    private final CatalogSettingsRepository settings = new CatalogSettingsRepository() {
        public Optional<CatalogSettings> find(UUID tenantId) { return Optional.ofNullable(stored.get(tenantId)); }
        public void save(UUID tenantId, CatalogSettings value) { stored.put(tenantId, value); }
    };
    private final ManageCatalogSettingsUseCase manage = new ManageCatalogSettingsUseCase(settings, clock);

    @Test void aTenantThatNeverSetTheFlagHasItOffAndNothingIsStoredByReading() {
        var current = manage.get(TENANT);
        assertThat(current.collaboratorEditing()).isFalse();
        assertThat(current.updatedAt()).isNull();
        assertThat(current.updatedBy()).isNull();
        assertThat(stored).isEmpty();
    }

    @Test void theAdministratorEnablesTheFlagAndTheAuthorAndTheInstantAreKept() {
        var result = manage.setCollaboratorEditing(TENANT, ACTOR, true);
        assertThat(result).isEqualTo(new CatalogSettings(true, clock.instant(), ACTOR));
        assertThat(manage.get(TENANT)).isEqualTo(result);
    }

    @Test void settingTheSameValueAgainLeavesTheSameStateAndDoesNotAdvanceTheInstant() {
        var first = manage.setCollaboratorEditing(TENANT, ACTOR, true);
        clock.advanceSeconds(60);
        var second = manage.setCollaboratorEditing(TENANT, UUID.randomUUID(), true);
        assertThat(second).isEqualTo(first);
        assertThat(stored.get(TENANT)).isEqualTo(first);
    }

    @Test void disablingAfterEnablingStoresFalseWithTheNewAuthorAndInstant() {
        manage.setCollaboratorEditing(TENANT, ACTOR, true);
        clock.advanceSeconds(60);
        Instant later = clock.instant();
        var other = UUID.randomUUID();
        var result = manage.setCollaboratorEditing(TENANT, other, false);
        assertThat(result).isEqualTo(new CatalogSettings(false, later, other));
    }

    @Test void disablingAFlagThatWasNeverSetStoresTheFalseValueAsAnExplicitRow() {
        var result = manage.setCollaboratorEditing(TENANT, ACTOR, false);
        assertThat(result.collaboratorEditing()).isFalse();
        assertThat(stored).containsKey(TENANT);
    }

    @Test void theFlagOfOneTenantNeverReachesAnother() {
        manage.setCollaboratorEditing(TENANT, ACTOR, true);
        assertThat(manage.get(OTHER_TENANT).collaboratorEditing()).isFalse();
    }
}
