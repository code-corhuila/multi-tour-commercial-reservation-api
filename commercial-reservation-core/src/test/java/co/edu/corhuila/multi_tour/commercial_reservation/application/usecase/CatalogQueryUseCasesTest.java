package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTermsChange;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogQueryUseCasesTest extends UseCaseFixture {

    @Test
    void inactivatedItemIsNotOfferedAndKeepsItsHistory() {
        CatalogService kept = plantTour(TENANT, "Chicamocha Canyon");
        CatalogService inactivated = plantTour(TENANT, "Old town walk");

        inactivate.inactivate(TENANT, inactivated.serviceId(), ACTOR);

        assertThat(listOffered.list(TENANT)).extracting(CatalogService::serviceId).containsExactly(kept.serviceId());
        CatalogService history = get.get(TENANT, inactivated.serviceId());
        assertThat(history.commercialName()).isEqualTo("Old town walk");
        assertThat(history.terms()).isEqualTo(inactivated.terms());
        assertThat(history.createdAt()).isEqualTo(inactivated.createdAt());
    }

    @Test
    void reactivationKeepsHistoryAndBypassesNoRestriction() {
        CatalogService planted = plantInactiveTour(TENANT, "Chicamocha Canyon");
        assertThat(listOffered.list(TENANT)).isEmpty();

        reactivate.reactivate(TENANT, planted.serviceId(), ACTOR);

        assertThat(listOffered.list(TENANT)).extracting(CatalogService::serviceId).containsExactly(planted.serviceId());
        CatalogService back = get.get(TENANT, planted.serviceId());
        assertThat(back.terms()).isEqualTo(planted.terms());
        assertThat(back.createdAt()).isEqualTo(planted.createdAt());
    }

    @Test
    void managementListingDoesNotFilterInactiveItems() {
        CatalogService active = plantTour(TENANT, "Chicamocha Canyon");
        CatalogService inactive = plantInactiveTour(TENANT, "Old town walk");

        assertThat(listForManagement.list(TENANT, Optional.empty(), Optional.empty(), 1, 20).items()).extracting(CatalogService::serviceId)
                .containsExactly(inactive.serviceId(), active.serviceId());
        assertThat(listForManagement.list(TENANT, Optional.of(CatalogStatus.INACTIVE), Optional.empty(), 1, 20).items())
                .extracting(CatalogService::serviceId).containsExactly(inactive.serviceId());
        assertThat(listForManagement.list(TENANT, Optional.of(CatalogStatus.ACTIVE), Optional.empty(), 1, 20).items())
                .extracting(CatalogService::serviceId).containsExactly(active.serviceId());
    }

    @Test
    void offerListingFiltersInactiveItems() {
        CatalogService active = plantTour(TENANT, "Chicamocha Canyon");
        plantInactiveTour(TENANT, "Old town walk");

        assertThat(listOffered.list(TENANT)).extracting(CatalogService::serviceId).containsExactly(active.serviceId());
    }

    @Test
    void readByIdentifierResolvesAnInactiveItem() {
        CatalogService inactive = plantInactiveTour(TENANT, "Old town walk");

        CatalogService found = get.get(TENANT, inactive.serviceId());

        assertThat(found.serviceId()).isEqualTo(inactive.serviceId());
        assertThat(found.catalogStatus()).isEqualTo(CatalogStatus.INACTIVE);
    }

    @Test
    void readingAnUnknownItemIsNotFound() {
        assertThatThrownBy(() -> get.get(TENANT, new UUID(9, 9))).isInstanceOf(CatalogServiceNotFoundException.class);
    }

    @Test
    void anItemOfAnotherTenantIsNeverReturned() {
        CatalogService foreign = plantTour(OTHER_TENANT, "Foreign tour");
        UUID id = foreign.serviceId();

        assertThatThrownBy(() -> get.get(TENANT, id)).isInstanceOf(CatalogServiceNotFoundException.class);
        assertThatThrownBy(() -> updateTerms.update(TENANT, id, new CommercialTermsChange(1L, null, null, null, null)))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThatThrownBy(() -> inactivate.inactivate(TENANT, id, ACTOR))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThat(listForManagement.list(TENANT, Optional.empty(), Optional.empty(), 1, 20).items()).isEmpty();
        assertThat(listOffered.list(TENANT)).isEmpty();
        assertThat(repository.findByTenantAndId(OTHER_TENANT, id).orElseThrow().catalogStatus())
                .isEqualTo(CatalogStatus.ACTIVE);
    }

    @Test
    void aRepositoryThatForgetsTheTenantPredicateStillLeaksNothing() {
        // Isolation must not rest on every future adapter remembering its WHERE clause (RN-09).
        InMemoryCatalogServiceRepository leaky = new InMemoryCatalogServiceRepository() {
            @Override
            public Optional<CatalogService> findByTenantAndId(UUID tenantId, UUID serviceId) {
                return super.findByTenantAndId(OTHER_TENANT, serviceId);
            }

            @Override
            public List<CatalogService> listByTenant(UUID tenantId, Optional<CatalogStatus> status) {
                return super.listByTenant(OTHER_TENANT, status);
            }
        };
        CatalogService foreign = CatalogService.register(new UUID(0, 50), OTHER_TENANT,
                CatalogServiceType.TOUR, "Foreign tour",
                CommercialTerms.of(1L, 1L, 1, false, false),
                START);
        leaky.plant(foreign);

        assertThatThrownBy(() -> new GetCatalogServiceUseCase(leaky).get(TENANT, foreign.serviceId()))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThat(new ListCatalogServicesForManagementUseCase(leaky)
                .list(TENANT, Optional.empty(), Optional.empty(), 1, 20).items()).isEmpty();
        assertThat(new ListOfferedCatalogServicesUseCase(leaky).list(TENANT)).isEmpty();
    }
    @Test
    void managementPagesKeepTheMatchingTotalAndApplyTypeAndStatusTogether() {
        var active = plantTour(TENANT, "Active tour");
        var inactive = plantInactiveTour(TENANT, "Inactive tour");
        plantTour(OTHER_TENANT, "Foreign tour");
        repository.plant(CatalogService.register(new UUID(0, 90), TENANT, CatalogServiceType.TRANSPORTATION,
                "Transport", CommercialTerms.of(10L, 5L, null, false, false), START));
        var second = listForManagement.list(TENANT, Optional.empty(), Optional.empty(), 2, 1);
        assertThat(second.total()).isEqualTo(3);
        assertThat(second.items()).extracting(CatalogService::serviceId).containsExactly(active.serviceId());
        var filtered = listForManagement.list(TENANT, Optional.of(CatalogStatus.INACTIVE),
                Optional.of(CatalogServiceType.TOUR), 1, 20);
        assertThat(filtered.total()).isEqualTo(1);
        assertThat(filtered.items()).extracting(CatalogService::serviceId).containsExactly(inactive.serviceId());
        var beyond = listForManagement.list(TENANT, Optional.empty(), Optional.empty(), Integer.MAX_VALUE, 20);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(3);
    }
}
