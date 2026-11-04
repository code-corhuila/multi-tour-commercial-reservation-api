package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// The quote use case of HU-COM-007: the tour comes from the catalog of the tenant, the date from the clock, the value from ReservationPricing.
class QuoteReservationTest {

    private static final UUID TENANT = new UUID(7, 0xc1), OTHER = new UUID(7, 0xc2), SERVICE = new UUID(7, 0xc3), DEPARTURE = new UUID(7, 0xc4);
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private final InMemoryCatalogServiceRepository catalog = new InMemoryCatalogServiceRepository();
    private final DiscountRuleFakes rules = new DiscountRuleFakes();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC);
    private final EvaluateApplicableDiscountRulesUseCase evaluation = new EvaluateApplicableDiscountRulesUseCase(rules);
    private final QuoteReservationUseCase quote = new QuoteReservationUseCase(catalog,
            new ReservationPricing(new QuoteDiscountBreakdownUseCase(evaluation), evaluation, (t, s, d) -> Optional.empty()), clock);

    private void plantTour() {
        catalog.save(CatalogService.register(SERVICE, TENANT, CatalogServiceType.TOUR, "Cocora",
                CommercialTerms.of(100_000L, 40_000L, 20, false, false), CREATED));
    }

    @Test
    void theQuoteUsesTheTourOfTheTenantAndTheRulesInForceOnTheDateOfTheClock() {
        plantTour();
        rules.save(DiscountRule.create(new UUID(7, 1), TENANT, "Today only", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28), 1,
                DiscountBase.ORIGINAL_VALUE, 1000, null, ServiceCoverage.all(), null, CREATED));

        ReservationQuote result = quote.quote(TENANT, SERVICE, DEPARTURE, 2);

        assertThat(result.projectedValue()).isEqualTo(new Cents(200_000));
        assertThat(result.finalValue()).isEqualTo(new Cents(180_000));
    }

    @Test
    void aTourOfAnotherTenantLooksMissing() {
        plantTour();

        assertThatThrownBy(() -> quote.quote(OTHER, SERVICE, DEPARTURE, 2)).isInstanceOf(CatalogServiceNotFoundException.class);
    }

    @Test
    void fewerThanOnePersonIsNamedAsThePeopleField() {
        plantTour();

        assertThatThrownBy(() -> quote.quote(TENANT, SERVICE, DEPARTURE, 0))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("people"));
    }
}
