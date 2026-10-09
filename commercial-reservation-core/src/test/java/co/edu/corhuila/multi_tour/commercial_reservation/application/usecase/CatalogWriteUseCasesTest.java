package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTermsChange;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogWriteUseCasesTest extends UseCaseFixture {

    @Test
    void registerPersistsAnActiveItemWithAGeneratedIdAndTheClockInstant() {
        CatalogService created = register.register(tourCommand(35_000_000L, 21_000_000L, 18));

        Optional<CatalogService> stored = repository.findByTenantAndId(TENANT, new UUID(0, 1));
        assertThat(stored).isPresent();
        assertThat(stored.get().catalogStatus()).isEqualTo(CatalogStatus.ACTIVE);
        assertThat(stored.get().createdAt()).isEqualTo(START);
        assertThat(created.serviceId()).isEqualTo(new UUID(0, 1));
        assertThat(created.tenantId()).isEqualTo(TENANT);
    }

    @Test
    void registerRejectsAMissingCostAndPersistsNothing() {
        assertThatThrownBy(() -> register.register(tourCommand(35_000_000L, null, 18)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).field()).isEqualTo("baseOperationalCostCents");

        assertThat(repository.listByTenant(TENANT, Optional.empty())).isEmpty();
    }

    @Test
    void updatingCommercialTermsPersistsTheChangeAndKeepsTheIdentifier() {
        CatalogService planted = plantTour(TENANT, "Chicamocha Canyon");

        CatalogService updated = updateTerms.update(TENANT, planted.serviceId(),
                new CommercialTermsChange(40_000_000L, null, null, null, true));

        CatalogService stored = repository.findByTenantAndId(TENANT, planted.serviceId()).orElseThrow();
        assertThat(stored.terms().baseRate().amount()).isEqualTo(40_000_000L);
        assertThat(stored.terms().highRisk()).isTrue();
        assertThat(stored.updatedAt()).isEqualTo(clock.instant());
        assertThat(updated.serviceId()).isEqualTo(planted.serviceId());
    }

    @Test
    void aRejectedTermsUpdateLeavesTheStoredItemUntouched() {
        CatalogService planted = plantTour(TENANT, "Chicamocha Canyon");

        assertThatThrownBy(() -> updateTerms.update(TENANT, planted.serviceId(),
                new CommercialTermsChange(-1L, null, null, null, null)))
                .isInstanceOf(DomainException.class);

        assertThat(repository.findByTenantAndId(TENANT, planted.serviceId()).orElseThrow().terms())
                .isEqualTo(planted.terms());
    }

    @Test
    void updatingDescriptiveInformationChangesOnlyTheName() {
        CatalogService planted = plantTour(TENANT, "Chicamocha Canyon");

        updateDescription.update(TENANT, planted.serviceId(), "Chicamocha Canyon at dawn");

        CatalogService stored = repository.findByTenantAndId(TENANT, planted.serviceId()).orElseThrow();
        assertThat(stored.commercialName()).isEqualTo("Chicamocha Canyon at dawn");
        assertThat(stored.terms()).isEqualTo(planted.terms());
    }

    @Test
    void updatingAnUnknownItemIsNotFound() {
        UUID unknown = new UUID(9, 9);

        assertThatThrownBy(() -> updateTerms.update(TENANT, unknown, new CommercialTermsChange(1L, null, null, null, null)))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThatThrownBy(() -> updateDescription.update(TENANT, unknown, "name"))
                .isInstanceOf(CatalogServiceNotFoundException.class);
    }
}
