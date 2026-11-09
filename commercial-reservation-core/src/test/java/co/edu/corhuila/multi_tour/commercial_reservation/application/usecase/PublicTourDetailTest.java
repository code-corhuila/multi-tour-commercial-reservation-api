package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicDeparture;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTourDetail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PublicOfferNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-006 T-04: RN-01, RN-02, RN-06, RN-09, AC-06.3, AC-06.8, AC-06.9, AC-06.11.
class PublicTourDetailTest extends PublicOfferFixture {

    private final DepartureFakes departures = new DepartureFakes();
    private final GetPublicTourUseCase detail = new GetPublicTourUseCase(directory, repository,
            new QuoteDiscountBreakdownUseCase(new EvaluateApplicableDiscountRulesUseCase(new DiscountRuleFakes())),
            departures, clock);

    private PublicTourDetail read(String identifier, UUID serviceId) {
        return detail.get(identifier, serviceId);
    }

    private DepartureState departure(CatalogService tour, DepartureStatus status, Duration fromNow, int capacity, int held) {
        DepartureState row = new DepartureState(ids.newId(), tour.tenantId(), tour.serviceId(),
                clock.instant().plus(fromNow), status, capacity, held);
        departures.plant(row);
        return row;
    }

    @Test
    void onlyAScheduledFutureDepartureWithSeatsIsOffered() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 10, false);
        DepartureState offered = departure(tour, DepartureStatus.SCHEDULED, Duration.ofDays(2), 10, 4);
        departure(tour, DepartureStatus.SCHEDULED, Duration.ofDays(3), 10, 10);
        departure(tour, DepartureStatus.SCHEDULED, Duration.ofDays(4), 0, 0);
        departure(tour, DepartureStatus.CANCELLED, Duration.ofDays(5), 10, 0);
        departure(tour, DepartureStatus.IN_EXECUTION, Duration.ofDays(6), 10, 0);
        departure(tour, DepartureStatus.FINALIZED, Duration.ofDays(7), 10, 0);
        departure(tour, DepartureStatus.SCHEDULED, Duration.ofHours(-1), 10, 0);

        PublicTourDetail result = read(IDENTIFIER, tour.serviceId());

        assertThat(result.departures()).containsExactly(new PublicDeparture(offered.departureId(), offered.startsAt(), 6));
    }

    @Test
    void aDepartureStartingNowIsNotOffered() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 10, false);
        departure(tour, DepartureStatus.SCHEDULED, Duration.ZERO, 10, 0);

        assertThat(read(IDENTIFIER, tour.serviceId()).departures()).isEmpty();
    }

    @Test
    void departuresComeOrderedByStartAndOverHeldSeatsNeverShowNegative() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 10, false);
        DepartureState later = departure(tour, DepartureStatus.SCHEDULED, Duration.ofDays(9), 8, 0);
        DepartureState sooner = departure(tour, DepartureStatus.SCHEDULED, Duration.ofDays(1), 8, 7);

        assertThat(read(IDENTIFIER, tour.serviceId()).departures()).extracting(PublicDeparture::departureId)
                .containsExactly(sooner.departureId(), later.departureId());
    }

    @Test
    void aTourWithNoDepartureAnswersAnEmptyListAndTheMaximumTravellers() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 12, true);

        PublicTourDetail result = read(IDENTIFIER, tour.serviceId());

        assertThat(result.departures()).isEmpty();
        assertThat(result.maxTravelers()).isEqualTo(12);
        assertThat(result.transportIncluded()).isTrue();
        assertThat(result.baseValueCents()).isEqualTo(100_000);
        assertThat(result.finalValueCents()).isEqualTo(100_000);
        assertThat(result.discountPercentageBp()).isNull();
    }

    @Test
    void theDeparturesOfAnotherTourOrTenantAreNeverMixedIn() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 10, false);
        CatalogService other = plantTourWith(TENANT, "Coffee", 100_000, 10, false);
        CatalogService foreign = plantTourWith(OTHER_TENANT, "Foreign", 100_000, 10, false);
        departure(other, DepartureStatus.SCHEDULED, Duration.ofDays(2), 10, 0);
        departure(foreign, DepartureStatus.SCHEDULED, Duration.ofDays(2), 10, 0);

        assertThat(read(IDENTIFIER, tour.serviceId()).departures()).isEmpty();
    }

    @Test
    void anInactiveTransportForeignOrUnknownItemAnswersTheSameNotFound() {
        CatalogService inactive = plantInactiveTour(TENANT, "Old walk");
        CatalogService transport = plantTransport(TENANT, "Shuttle");
        CatalogService foreign = plantTourWith(OTHER_TENANT, "Foreign", 100_000, 10, false);

        for (UUID serviceId : new UUID[] {inactive.serviceId(), transport.serviceId(), foreign.serviceId(), UUID.randomUUID()}) {
            assertThatThrownBy(() -> read(IDENTIFIER, serviceId)).isInstanceOf(PublicOfferNotFoundException.class);
        }
    }

    @Test
    void anUnknownInactiveOrMalformedTenantFailsBeforeReadingTheTour() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 10, false);

        assertThatThrownBy(() -> read("unknown-tenant", tour.serviceId())).isInstanceOf(PublicOfferNotFoundException.class);
        assertThatThrownBy(() -> read(INACTIVE_IDENTIFIER, tour.serviceId())).isInstanceOf(PublicOfferNotFoundException.class);
        assertThatThrownBy(() -> read("Not Valid!", tour.serviceId())).isInstanceOf(DomainException.class);
    }
}
