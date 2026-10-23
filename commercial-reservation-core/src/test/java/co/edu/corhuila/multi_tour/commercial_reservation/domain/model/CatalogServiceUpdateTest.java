package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.ID;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.NOW;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.tour;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CatalogServiceUpdateTest {

    static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    void updateKeepsTheIdentifier() {
        CatalogService item = tour(false);

        item.updateCommercialTerms(new CommercialTermsChange(40_000_000L, 25_000_000L, 20, null, null), LATER);
        item.updateDescriptiveInformation("Chicamocha Canyon Deluxe", LATER);

        assertThat(item.serviceId()).isEqualTo(ID);
        assertThat(item.tenantId()).isEqualTo(TENANT);
        assertThat(item.terms().baseRate().amount()).isEqualTo(40_000_000L);
        assertThat(item.terms().baseOperationalCost().amount()).isEqualTo(25_000_000L);
        assertThat(item.terms().configuredCapacity()).isEqualTo(20);
        assertThat(item.commercialName()).isEqualTo("Chicamocha Canyon Deluxe");
        assertThat(item.createdAt()).isEqualTo(NOW);
        assertThat(item.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void aPartialChangeLeavesTheOtherTermsUntouched() {
        CatalogService item = tour(false);

        item.updateCommercialTerms(new CommercialTermsChange(null, null, null, false, null), LATER);

        assertThat(item.terms().transportRequired()).isFalse();
        assertThat(item.terms().baseRate().amount()).isEqualTo(35_000_000L);
        assertThat(item.terms().configuredCapacity()).isEqualTo(18);
    }

    @Test
    void highRiskMarkIsSetOnRegisterAndUpdate() {
        CatalogService item = tour(true);
        assertThat(item.terms().highRisk()).isTrue();

        item.updateCommercialTerms(new CommercialTermsChange(null, null, null, null, false), LATER);
        assertThat(item.terms().highRisk()).isFalse();

        item.updateCommercialTerms(new CommercialTermsChange(null, null, null, null, true), LATER);
        assertThat(item.terms().highRisk()).isTrue();
    }

    @Test
    void anUpdateRunsTheSameInvariantsAsTheRegistration() {
        CatalogService item = tour(false);

        assertThatThrownBy(() -> item.updateCommercialTerms(new CommercialTermsChange(-5L, null, null, null, null), LATER))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).field()).isEqualTo("baseRateCents");
        assertThatThrownBy(() -> item.updateCommercialTerms(new CommercialTermsChange(null, null, 0, null, null), LATER))
                .isInstanceOf(DomainException.class);
        assertThat(item.terms().baseRate().amount()).isEqualTo(35_000_000L);
        assertThat(item.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void aTransportationItemStaysAtFalseWhenAnUpdateAsksForTransportRequired() {
        CatalogService transport = CatalogService.register(ID, TENANT, CatalogServiceType.TRANSPORTATION,
                "Bus to the canyon", CommercialTerms.of(12_000L, 8_000L, null, false, false), NOW);

        transport.updateCommercialTerms(new CommercialTermsChange(13_000L, null, null, true, null), LATER);

        assertThat(transport.terms().transportRequired()).isFalse();
        assertThat(transport.terms().baseRate().amount()).isEqualTo(13_000L);
    }

    // AC-01.6, RN-04, plan D-03: one method per intent, so a caller of one intent cannot reach the fields of the other.
    @Test
    void theDescriptiveIntentLeavesEveryCommercialTermUntouched() {
        CatalogService item = tour(true);
        CommercialTerms before = item.terms();

        item.updateDescriptiveInformation("Renamed tour", LATER);

        assertThat(item.commercialName()).isEqualTo("Renamed tour");
        assertThat(item.terms()).isEqualTo(before);
        assertThat(item.catalogStatus()).isEqualTo(CatalogStatus.ACTIVE);
        assertThat(item.serviceType()).isEqualTo(CatalogServiceType.TOUR);
    }

    @Test
    void theCommercialIntentLeavesTheDescriptiveInformationAndTheStatusUntouched() {
        CatalogService item = tour(false);
        String name = item.commercialName();

        item.updateCommercialTerms(new CommercialTermsChange(40_000_000L, 25_000_000L, 20, true, true), LATER);

        assertThat(item.commercialName()).isEqualTo(name);
        assertThat(item.catalogStatus()).isEqualTo(CatalogStatus.ACTIVE);
        assertThat(item.pullEvents()).isEmpty();
    }

    @Test
    void theTwoIntentsTakeDifferentArgumentsSoNeitherCanCarryTheOthersFields() {
        assertThat(java.util.Arrays.stream(CommercialTermsChange.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("commercialName");
        assertThat(java.util.Arrays.stream(CatalogService.class.getMethods())
                .filter(m -> m.getName().equals("updateDescriptiveInformation")).findFirst().orElseThrow().getParameterTypes())
                .containsExactly(String.class, Instant.class);
    }

    @Test
    void theCommercialNameMustBeNonBlankAndAtMost255Characters() {
        assertThatThrownBy(() -> CatalogService.register(ID, TENANT, CatalogServiceType.TOUR, "  ",
                CommercialTerms.of(1L, 1L, 1, false, false), NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).field()).isEqualTo("commercialName");

        CatalogService item = tour(false);
        assertThatThrownBy(() -> item.updateDescriptiveInformation("x".repeat(256), LATER))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).field()).isEqualTo("commercialName");
        item.updateDescriptiveInformation("x".repeat(255), LATER);
        assertThat(item.commercialName()).hasSize(255);
    }
}
