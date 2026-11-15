package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.ID;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.NOW;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

// RN-10 (RN-TRA-002, gap G-E closed by default in 06-data): the per-person transport rate is an optional field of the
// aggregate, in whole cents, never negative; absent means "not parametrized".
class CatalogServiceTransportRateTest {

    static final Instant LATER = NOW.plusSeconds(3600);

    private static CatalogService tourWithRate(Long rate) {
        return CatalogService.register(ID, TENANT, CatalogServiceType.TOUR, "Chicamocha Canyon",
                CommercialTerms.of(35_000_000L, 21_000_000L, 18, true, false, rate), NOW);
    }

    @Test
    void anItemWithoutARateIsNotParametrized() {
        assertThat(tourWithRate(null).terms().transportRatePerPerson()).isNull();
        assertThat(CommercialTerms.of(1L, 1L, 18, true, false).transportRatePerPerson()).isNull();
    }

    @Test
    void aRateIsHeldInWholeCentsAndZeroIsTransportAtNoCharge() {
        assertThat(tourWithRate(25_000L).terms().transportRatePerPerson().amount()).isEqualTo(25_000L);
        assertThat(tourWithRate(0L).terms().transportRatePerPerson().amount()).isZero();
    }

    @Test
    void aNegativeRateIsRefusedAndNamesItsField() {
        assertThatThrownBy(() -> tourWithRate(-1L)).isInstanceOf(DomainException.class)
                .hasMessageContaining("transportRatePerPersonCents");
    }

    @Test
    void transportRatePerPersonIsStoredAndUpdatedByTheAggregate() {
        CatalogService item = tourWithRate(null);

        item.updateCommercialTerms(new CommercialTermsChange(null, null, null, null, null, 30_000L), LATER);
        assertThat(item.terms().transportRatePerPerson().amount()).isEqualTo(30_000L);

        item.updateCommercialTerms(new CommercialTermsChange(40_000_000L, null, null, null, null), LATER);
        assertThat(item.terms().transportRatePerPerson().amount()).isEqualTo(30_000L);
        assertThat(item.terms().baseRate().amount()).isEqualTo(40_000_000L);

        assertThatThrownBy(() -> item.updateCommercialTerms(
                new CommercialTermsChange(null, null, null, null, null, -5L), LATER))
                .isInstanceOf(DomainException.class).hasMessageContaining("transportRatePerPersonCents");
        assertThat(item.terms().transportRatePerPerson().amount()).isEqualTo(30_000L);
    }

    @Test
    void aTransportItemKeepsItsRateWhenTheTransportFlagIsForcedOff() {
        CatalogService transport = CatalogService.register(ID, TENANT, CatalogServiceType.TRANSPORTATION, "Transfer",
                CommercialTerms.of(80_000L, 50_000L, null, false, false, 12_000L), NOW);

        assertThat(transport.terms().transportRequired()).isFalse();
        assertThat(transport.terms().transportRatePerPerson().amount()).isEqualTo(12_000L);
    }
}
