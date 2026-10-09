package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.ID;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.NOW;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceRegistrationTest.tour;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogServiceStatusTest {

    static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    void inactivatingEmitsTheEventWithItsPayload() {
        CatalogService item = tour(false);

        item.inactivate(ACTOR, LATER);

        assertThat(item.catalogStatus()).isEqualTo(CatalogStatus.INACTIVE);
        assertThat(item.pullEvents()).containsExactly(new CatalogServiceInactivated(TENANT, ID,
                CatalogStatus.ACTIVE, CatalogStatus.INACTIVE, ACTOR, LATER));
    }

    @Test
    void inactivatingTwiceEmitsOneEvent() {
        CatalogService item = tour(false);

        item.inactivate(ACTOR, LATER);
        item.inactivate(ACTOR, LATER.plusSeconds(60));

        assertThat(item.catalogStatus()).isEqualTo(CatalogStatus.INACTIVE);
        assertThat(item.pullEvents()).hasSize(1);
        assertThat(item.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void reactivatingTwiceEmitsOneEvent() {
        CatalogService item = tour(false);
        item.inactivate(ACTOR, LATER);
        item.pullEvents();

        item.reactivate(ACTOR, LATER.plusSeconds(60));
        item.reactivate(ACTOR, LATER.plusSeconds(120));

        assertThat(item.catalogStatus()).isEqualTo(CatalogStatus.ACTIVE);
        assertThat(item.pullEvents()).containsExactly(new CatalogServiceReactivated(TENANT, ID,
                CatalogStatus.INACTIVE, CatalogStatus.ACTIVE, ACTOR, LATER.plusSeconds(60)));
    }

    @Test
    void reactivatingAnActiveItemEmitsNothing() {
        CatalogService item = tour(false);

        item.reactivate(ACTOR, LATER);

        assertThat(item.pullEvents()).isEmpty();
        assertThat(item.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void pullingTheEventsDrainsThem() {
        CatalogService item = tour(false);
        item.inactivate(ACTOR, LATER);

        assertThat(item.pullEvents()).hasSize(1);
        assertThat(item.pullEvents()).isEmpty();
    }

    @Test
    void reactivationKeepsHistoryAndBypassesNoRestriction() {
        CatalogService healthy = CatalogService.restore(ID, TENANT, CatalogServiceType.TOUR, "Chicamocha Canyon",
                CatalogStatus.INACTIVE, CommercialTerms.of(35_000_000L, 21_000_000L, 18, true, true), NOW, NOW);

        healthy.reactivate(ACTOR, LATER);

        assertThat(healthy.catalogStatus()).isEqualTo(CatalogStatus.ACTIVE);
        assertThat(healthy.serviceId()).isEqualTo(ID);
        assertThat(healthy.createdAt()).isEqualTo(NOW);
        assertThat(healthy.terms()).isEqualTo(CommercialTerms.of(35_000_000L, 21_000_000L, 18, true, true));

        // A row stored before today's invariants (a tour with no capacity) cannot slip back into the offer.
        CatalogService stale = CatalogService.restore(ID, TENANT, CatalogServiceType.TOUR, "Chicamocha Canyon",
                CatalogStatus.INACTIVE, new CommercialTerms(new Cents(1), new Cents(1), null, false, false), NOW, NOW);

        assertThatThrownBy(() -> stale.reactivate(ACTOR, LATER)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).field()).isEqualTo("configuredCapacity");
        assertThat(stale.catalogStatus()).isEqualTo(CatalogStatus.INACTIVE);
        assertThat(stale.pullEvents()).isEmpty();
    }

    @Test
    void anInactiveItemKeepsEveryFieldItHad() {
        CatalogService item = tour(true);

        item.inactivate(ACTOR, LATER);

        assertThat(item.commercialName()).isEqualTo("Chicamocha Canyon");
        assertThat(item.terms()).isEqualTo(CommercialTerms.of(35_000_000L, 21_000_000L, 18, true, true));
        assertThat(item.createdAt()).isEqualTo(NOW);
    }
}
