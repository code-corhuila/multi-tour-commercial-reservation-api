package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceInactivated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceReactivated;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogStatusUseCasesTest extends UseCaseFixture {

    @Test
    void inactivatingPersistsTheStateAndWritesOneEvent() {
        CatalogService planted = plantTour(TENANT, "Chicamocha Canyon");

        inactivate.inactivate(TENANT, planted.serviceId(), ACTOR);

        assertThat(repository.findByTenantAndId(TENANT, planted.serviceId()).orElseThrow().catalogStatus())
                .isEqualTo(CatalogStatus.INACTIVE);
        assertThat(repository.outbox()).containsExactly(new CatalogServiceInactivated(TENANT, planted.serviceId(),
                CatalogStatus.ACTIVE, CatalogStatus.INACTIVE, ACTOR, clock.instant()));
    }

    @Test
    void inactivatingTwiceEmitsOneEvent() {
        CatalogService planted = plantTour(TENANT, "Chicamocha Canyon");

        inactivate.inactivate(TENANT, planted.serviceId(), ACTOR);
        clock.advanceSeconds(60);
        inactivate.inactivate(TENANT, planted.serviceId(), ACTOR);

        assertThat(repository.outbox()).hasSize(1);
        assertThat(repository.findByTenantAndId(TENANT, planted.serviceId()).orElseThrow().catalogStatus())
                .isEqualTo(CatalogStatus.INACTIVE);
    }

    @Test
    void reactivatingPersistsTheStateAndWritesOneEvent() {
        CatalogService planted = plantInactiveTour(TENANT, "Chicamocha Canyon");

        reactivate.reactivate(TENANT, planted.serviceId(), ACTOR);

        assertThat(repository.findByTenantAndId(TENANT, planted.serviceId()).orElseThrow().catalogStatus())
                .isEqualTo(CatalogStatus.ACTIVE);
        assertThat(repository.outbox()).containsExactly(new CatalogServiceReactivated(TENANT, planted.serviceId(),
                CatalogStatus.INACTIVE, CatalogStatus.ACTIVE, ACTOR, clock.instant()));
    }

    @Test
    void reactivatingTwiceEmitsOneEvent() {
        CatalogService planted = plantInactiveTour(TENANT, "Chicamocha Canyon");

        reactivate.reactivate(TENANT, planted.serviceId(), ACTOR);
        clock.advanceSeconds(60);
        reactivate.reactivate(TENANT, planted.serviceId(), ACTOR);

        assertThat(repository.outbox()).hasSize(1);
    }

    @Test
    void aStaleRowCannotBeReactivatedAndNothingIsWritten() {
        UUID id = new UUID(0, 77);
        repository.plant(CatalogService.restore(id, TENANT, CatalogServiceType.TOUR, "Old tour",
                CatalogStatus.INACTIVE, new CommercialTerms(new Cents(1), new Cents(1), null, false, false),
                START, START));

        assertThatThrownBy(() -> reactivate.reactivate(TENANT, id, ACTOR)).isInstanceOf(BusinessRuleViolation.class);

        assertThat(repository.findByTenantAndId(TENANT, id).orElseThrow().catalogStatus())
                .isEqualTo(CatalogStatus.INACTIVE);
        assertThat(repository.outbox()).isEmpty();
    }

    @Test
    void changingTheStatusOfAnUnknownItemIsNotFound() {
        UUID unknown = new UUID(9, 9);

        assertThatThrownBy(() -> inactivate.inactivate(TENANT, unknown, ACTOR))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThatThrownBy(() -> reactivate.reactivate(TENANT, unknown, ACTOR))
                .isInstanceOf(CatalogServiceNotFoundException.class);
    }
}
