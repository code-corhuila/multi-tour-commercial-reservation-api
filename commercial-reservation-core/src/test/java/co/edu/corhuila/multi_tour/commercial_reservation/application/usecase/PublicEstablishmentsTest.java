package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PublicOfferNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-006 T-05 (core half): RN-05, AC-06.6, AC-06.7, AC-06.17.
class PublicEstablishmentsTest extends PublicOfferFixture {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    private final InMemoryAssociatedEstablishmentRepository establishments = new InMemoryAssociatedEstablishmentRepository();
    private final ListPublicEstablishmentsUseCase list = new ListPublicEstablishmentsUseCase(directory, establishments);
    private final GetPublicEstablishmentUseCase get = new GetPublicEstablishmentUseCase(directory, establishments);

    private AssociatedEstablishment plant(UUID tenant, long id, EstablishmentType type, boolean active) {
        AssociatedEstablishment item = AssociatedEstablishment.register(new UUID(5, id), tenant, type, "Place " + id,
                "Contact details " + id, "opaque-reference", ACTOR, T0.plusSeconds(id));
        if (!active) {
            item.inactivate(ACTOR, T0.plusSeconds(100));
        }
        item.pullEvents();
        establishments.plant(item);
        return item;
    }

    @Test
    void onlyActiveEstablishmentsOfTheTenantAreListed() {
        AssociatedEstablishment active = plant(TENANT, 1, EstablishmentType.HOTEL, true);
        plant(TENANT, 2, EstablishmentType.HOTEL, false);
        plant(OTHER_TENANT, 3, EstablishmentType.HOTEL, true);

        var result = list.list(IDENTIFIER, Optional.empty(), 1, 20);

        assertThat(result.items()).extracting(PublicEstablishment::associationId).containsExactly(active.associationId());
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void theTypeIsTheOnlyFilterAndThePageIsBounded() {
        plant(TENANT, 1, EstablishmentType.HOTEL, true);
        plant(TENANT, 2, EstablishmentType.RESTAURANT, true);

        assertThat(list.list(IDENTIFIER, Optional.of(EstablishmentType.RESTAURANT), 1, 20).items())
                .extracting(PublicEstablishment::establishmentType).containsExactly(EstablishmentType.RESTAURANT);
        assertThat(list.list(IDENTIFIER, Optional.empty(), 2, 1).items()).hasSize(1);
        assertThatThrownBy(() -> list.list(IDENTIFIER, Optional.empty(), 1, 101)).hasMessageContaining("limit");
    }

    @Test
    void anUnknownOrInactiveTenantAnswersNotFound() {
        assertThatThrownBy(() -> list.list("unknown-tenant", Optional.empty(), 1, 20))
                .isInstanceOf(PublicOfferNotFoundException.class);
        assertThatThrownBy(() -> get.get(INACTIVE_IDENTIFIER, new UUID(5, 1)))
                .isInstanceOf(PublicOfferNotFoundException.class);
    }

    @Test
    void anInactiveEstablishmentReadByIdentifierIsNotFound() {
        AssociatedEstablishment inactive = plant(TENANT, 1, EstablishmentType.HOTEL, false);
        AssociatedEstablishment foreign = plant(OTHER_TENANT, 2, EstablishmentType.HOTEL, true);

        assertThatThrownBy(() -> get.get(IDENTIFIER, inactive.associationId()))
                .isInstanceOf(PublicOfferNotFoundException.class);
        assertThatThrownBy(() -> get.get(IDENTIFIER, foreign.associationId()))
                .isInstanceOf(PublicOfferNotFoundException.class);
        assertThatThrownBy(() -> get.get(IDENTIFIER, new UUID(9, 9))).isInstanceOf(PublicOfferNotFoundException.class);
    }

    @Test
    void theImageAddressPointsToTheExistingPublicRoute() {
        AssociatedEstablishment active = plant(TENANT, 1, EstablishmentType.HOTEL, true);

        PublicEstablishment found = get.get(IDENTIFIER, active.associationId());

        assertThat(found.imageUrl()).isEqualTo("/api/v1/commercial-reservation/public/tenants/" + IDENTIFIER
                + "/associated-establishments/" + active.associationId() + "/image");
        assertThat(found.commercialInformation()).isEqualTo("Contact details 1");
        assertThat(found.commercialName()).isEqualTo("Place 1");
    }
}
