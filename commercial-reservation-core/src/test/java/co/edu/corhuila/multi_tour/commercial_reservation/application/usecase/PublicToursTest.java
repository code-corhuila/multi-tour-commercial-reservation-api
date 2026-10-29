package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTour;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTourPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PublicOfferNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-006 T-01 (tenant half), T-02, T-03: RN-01, RN-03, RN-04, RN-06, RN-07, AC-06.2, AC-06.4, AC-06.5, AC-06.9.
class PublicToursTest extends PublicOfferFixture {

    private final DiscountRuleFakes rules = new DiscountRuleFakes();
    private final ListPublicToursUseCase list = new ListPublicToursUseCase(directory, repository,
            new QuoteDiscountBreakdownUseCase(new EvaluateApplicableDiscountRulesUseCase(rules)), clock);

    private PublicTourPage page(String identifier) {
        return list.list(identifier, Optional.empty(), 1, 20);
    }

    @Test
    void anUnknownOrInactiveTenantAnswersNotFound() {
        plantTourWith(TENANT, "Canyon", 100_000, 10, false);

        assertThatThrownBy(() -> page("unknown-tenant")).isInstanceOf(PublicOfferNotFoundException.class);
        assertThatThrownBy(() -> page(INACTIVE_IDENTIFIER)).isInstanceOf(PublicOfferNotFoundException.class);
    }

    @Test
    void anIdentifierThatIsNotKebabCaseOrUuidIsAValidationErrorBeforeAnyLookup() {
        for (String malformed : new String[] {"Not Valid!", "a".repeat(65), null}) {
            assertThatThrownBy(() -> page(malformed)).isInstanceOf(DomainException.class)
                    .extracting(error -> ((DomainException) error).field()).isEqualTo("identifier");
        }
        assertThat(directory.lookups).isZero();
    }

    @Test
    void aWellFormedIdentifierOfTheMaximumLengthStaysNotFound() {
        assertThatThrownBy(() -> page("a".repeat(64))).isInstanceOf(PublicOfferNotFoundException.class);
    }

    @Test
    void onlyActiveToursAreOffered() {
        CatalogService active = plantTourWith(TENANT, "Canyon", 100_000, 10, false);
        plantInactiveTour(TENANT, "Old walk");
        plantTourWith(OTHER_TENANT, "Foreign tour", 100_000, 10, false);

        PublicTourPage result = page(IDENTIFIER);

        assertThat(result.items()).extracting(PublicTour::serviceId).containsExactly(active.serviceId());
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void aTransportSoldAloneIsNotOffered() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 10, false);
        plantTransport(TENANT, "Shuttle");

        assertThat(page(IDENTIFIER).items()).extracting(PublicTour::serviceId).containsExactly(tour.serviceId());
    }

    @Test
    void aTourWithMandatoryTransportSaysItIsIncluded() {
        plantTourWith(TENANT, "With bus", 100_000, 12, true);
        plantTourWith(TENANT, "Walking", 100_000, 8, false);

        assertThat(page(IDENTIFIER).items()).extracting(PublicTour::commercialName, PublicTour::transportIncluded,
                PublicTour::maxTravelers).containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Walking", false, 8),
                        org.assertj.core.groups.Tuple.tuple("With bus", true, 12));
    }

    @Test
    void listsArePaginatedAndSearchNarrowsTheTours() {
        plantTourWith(TENANT, "Canyon at dawn", 100_000, 10, false);
        plantTourWith(TENANT, "Coffee farm", 100_000, 10, false);
        plantTourWith(TENANT, "CANYON at night", 100_000, 10, false);

        assertThat(list.list(IDENTIFIER, Optional.empty(), 2, 2).items()).hasSize(1);
        PublicTourPage found = list.list(IDENTIFIER, Optional.of(" canyon "), 1, 20);
        assertThat(found.items()).extracting(PublicTour::commercialName)
                .containsExactly("CANYON at night", "Canyon at dawn");
        assertThat(found.total()).isEqualTo(2);
        assertThat(list.list(IDENTIFIER, Optional.of("   "), 1, 20).total()).isEqualTo(3);
    }

    @Test
    void aTenantWithNothingGetsAnEmptyPage() {
        PublicTourPage empty = page(OTHER_IDENTIFIER);

        assertThat(empty.items()).isEmpty();
        assertThat(empty.total()).isZero();
    }

    @Test
    void aPageOrLimitOutOfRangeIsAValidationError() {
        assertThatThrownBy(() -> list.list(IDENTIFIER, Optional.empty(), 0, 20)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> list.list(IDENTIFIER, Optional.empty(), 1, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> list.list(IDENTIFIER, Optional.empty(), 1, 101)).isInstanceOf(DomainException.class);
    }

    @Test
    void theValueComesFromTheDiscountFunctionAndNoRuleLeavesOnlyTheBase() {
        plantTourWith(TENANT, "Canyon", 100_000, 10, false);

        PublicTour bare = page(IDENTIFIER).items().get(0);
        assertThat(bare.baseValueCents()).isEqualTo(100_000);
        assertThat(bare.finalValueCents()).isEqualTo(100_000);
        assertThat(bare.discountPercentageBp()).isNull();

        rule("Early", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1500);
        PublicTour discounted = page(IDENTIFIER).items().get(0);
        assertThat(discounted.baseValueCents()).isEqualTo(100_000);
        assertThat(discounted.finalValueCents()).isEqualTo(85_000);
        assertThat(discounted.discountPercentageBp()).isEqualTo(1500);
    }

    @Test
    void expiredAndUpcomingRulesDoNotChangeTheValue() {
        plantTourWith(TENANT, "Canyon", 100_000, 10, false);
        rule("Expired", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 7), 1000);
        rule("Upcoming", LocalDate.of(2026, 10, 9), LocalDate.of(2026, 12, 31), 1000);

        PublicTour result = page(IDENTIFIER).items().get(0);

        assertThat(result.finalValueCents()).isEqualTo(result.baseValueCents());
        assertThat(result.discountPercentageBp()).isNull();
    }

    @Test
    void thePercentageIsIntegerArithmeticOnTheTotals() {
        plantTourWith(TENANT, "Canyon", 33_333, 10, false);
        rule("Third", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1000);

        PublicTour result = page(IDENTIFIER).items().get(0);

        // 3_333 * 10_000 / 33_333 truncates to 999, never a float.
        assertThat(result.finalValueCents()).isEqualTo(30_000);
        assertThat(result.discountPercentageBp()).isEqualTo(999);
    }

    @Test
    void theBaseNeverComesFromTheCallerAndTheDayIsTheUtcDayOfTheClock() {
        CatalogService tour = plantTourWith(TENANT, "Canyon", 100_000, 10, false);
        // Valid only on the UTC day of the clock (2026-10-08).
        rule("Today only", LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 8), 2000);

        PublicTour result = page(IDENTIFIER).items().get(0);

        assertThat(result.baseValueCents()).isEqualTo(tour.terms().baseRate().amount());
        assertThat(result.finalValueCents()).isEqualTo(80_000);
    }

    private void rule(String name, LocalDate from, LocalDate to, int basisPoints) {
        rules.save(DiscountRule.create(new UUID(2, rules.rules.size() + 1L), TENANT, name, from, to, 1,
                DiscountBase.ORIGINAL_VALUE, basisPoints, null, ServiceCoverage.all(), null,
                Instant.parse("2026-01-01T00:00:00Z")));
    }
}
