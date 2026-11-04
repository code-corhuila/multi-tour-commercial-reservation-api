package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TransportRateReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FixedDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-02 of HU-COM-007: RN-02, RN-03, RN-13, RN-15; AC-07.3 to AC-07.6. Values in cents, DP-01 (per person) and DP-03 defaults.
class ReservationPricingTest {

    private static final UUID TENANT = new UUID(7, 0xa1), SERVICE = new UUID(7, 0xa2), DEPARTURE = new UUID(7, 0xa3);
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private final DiscountRuleFakes rules = new DiscountRuleFakes();
    private final List<Object[]> calls = new ArrayList<>();
    private Optional<Cents> transportRate = Optional.of(new Cents(20_000));
    private final EvaluateApplicableDiscountRulesUseCase evaluation = new EvaluateApplicableDiscountRulesUseCase(rules);
    private final QuoteDiscountBreakdown recording = (tenant, service, date, base) -> {
        calls.add(new Object[] {tenant, service, date, base});
        return new QuoteDiscountBreakdownUseCase(evaluation).quote(tenant, service, date, base);
    };
    private final TransportRateReader transport = (tenant, tour, departure) -> transportRate;
    private final ReservationPricing pricing = new ReservationPricing(recording, evaluation, transport);
    private long seq = 1;

    private static CatalogService tour(CatalogServiceType type, boolean transportRequired) {
        return CatalogService.register(SERVICE, TENANT, type, "Cocora",
                CommercialTerms.of(100_000L, 40_000L, 20, transportRequired, false), CREATED);
    }

    private DiscountRule rule(String name, int priority, DiscountBase base, Integer bp, Long cents, Long cap) {
        DiscountRule rule = DiscountRule.create(new UUID(7, 0xb0 + seq++), TENANT, name, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), priority, base, bp, cents, ServiceCoverage.all(), cap, CREATED);
        rules.save(rule);
        return rule;
    }

    private ReservationQuote quote(CatalogService tour, int people) {
        return pricing.quote(TENANT, tour, DEPARTURE, people, TODAY);
    }

    @Test
    void projectedValueAddsTheTourAndPerPersonTransportInCents() {
        ReservationQuote quote = quote(tour(CatalogServiceType.TOUR, true), 3);

        assertThat(quote.tourLine()).isEqualTo(new Cents(300_000));
        assertThat(quote.transportLine()).isEqualTo(new Cents(60_000));
        assertThat(quote.projectedValue()).isEqualTo(new Cents(360_000));
        assertThat(quote.finalValue()).isEqualTo(new Cents(360_000));
        assertThat(quote.discounts()).isEmpty();
        assertThat(quote.service().baseRatePerPerson()).isEqualTo(new Cents(100_000));
        assertThat(quote.service().transportRatePerPerson()).isEqualTo(new Cents(20_000));
    }

    @Test
    void aTourThatDoesNotRequireTransportHasNoTransportLine() {
        ReservationQuote quote = quote(tour(CatalogServiceType.TOUR, false), 2);

        assertThat(quote.transportLine()).isNull();
        assertThat(quote.service().transportRatePerPerson()).isNull();
        assertThat(quote.projectedValue()).isEqualTo(new Cents(200_000));
    }

    @Test
    void aTourRequiringTransportWithNoRateIsNotQuoted() {
        transportRate = Optional.empty();

        assertThatThrownBy(() -> quote(tour(CatalogServiceType.TOUR, true), 2)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void theTransportIsChargedOnceAndATransportItemIsNotAcceptedAsAService() {
        ReservationQuote quote = quote(tour(CatalogServiceType.TOUR, true), 2);
        assertThat(quote.projectedValue()).isEqualTo(new Cents(2 * 100_000 + 2 * 20_000));

        assertThatThrownBy(() -> quote(tour(CatalogServiceType.TRANSPORTATION, false), 2))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void anInactiveTourIsNotQuoted() {
        CatalogService inactive = tour(CatalogServiceType.TOUR, false);
        inactive.inactivate(new UUID(7, 9), CREATED.plusSeconds(60));

        assertThatThrownBy(() -> quote(inactive, 2)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void eachDiscountLineNamesItsOrderBaseAndCap() {
        DiscountRule early = rule("Early", 1, DiscountBase.ORIGINAL_VALUE, 1000, null, 15_000L);
        DiscountRule fixed = rule("Loyal", 2, DiscountBase.PREVIOUS_SUBTOTAL, null, 5_000L, null);

        ReservationQuote quote = quote(tour(CatalogServiceType.TOUR, true), 3);

        assertThat(quote.discounts()).containsExactly(
                new FixedDiscount(1, early.ruleId(), "Early", DiscountType.PERCENTAGE, 1000, null,
                        DiscountBase.ORIGINAL_VALUE, new Cents(360_000), new Cents(15_000), new Cents(345_000), 15_000L),
                new FixedDiscount(2, fixed.ruleId(), "Loyal", DiscountType.FIXED_AMOUNT, null, 5_000L,
                        DiscountBase.PREVIOUS_SUBTOTAL, new Cents(345_000), new Cents(5_000), new Cents(340_000), null));
        assertThat(quote.finalValue()).isEqualTo(new Cents(340_000));
    }

    @Test
    void everyRuleInForceIsKeptEvenWhenItsAmountIsZero() {
        rule("Everything", 1, DiscountBase.ORIGINAL_VALUE, 10_000, null, null);
        DiscountRule nothingLeft = rule("Nothing left", 2, DiscountBase.PREVIOUS_SUBTOTAL, 1000, null, null);

        ReservationQuote quote = quote(tour(CatalogServiceType.TOUR, false), 1);

        assertThat(quote.discounts()).hasSize(2);
        assertThat(quote.discounts().get(1).ruleId()).isEqualTo(nothingLeft.ruleId());
        assertThat(quote.discounts().get(1).amount()).isEqualTo(new Cents(0));
        assertThat(quote.finalValue()).isEqualTo(new Cents(0));
    }

    @Test
    void theQuoteCallsTheDiscountCalculationOfHuCom004AndCopiesNothing() throws Exception {
        ReservationQuote quote = quote(tour(CatalogServiceType.TOUR, true), 2);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0)).containsExactly(TENANT, SERVICE, TODAY, quote.projectedValue());
        String source = Files.readString(Path.of("src/main/java/co/edu/corhuila/multi_tour/commercial_reservation"
                + "/application/usecase/ReservationPricing.java"));
        assertThat(source).doesNotContain("DiscountLine").doesNotContain("BasisPoints.").doesNotContain("new BasisPoints").doesNotContain("Math.");
    }

    @Test
    void operatorAndCustomerQuotesAreEqualForTheSameSelection() {
        rule("Early", 1, DiscountBase.ORIGINAL_VALUE, 1234, null, null);
        CatalogService tour = tour(CatalogServiceType.TOUR, true);

        assertThat(quote(tour, 4)).isEqualTo(quote(tour, 4));
    }
}
