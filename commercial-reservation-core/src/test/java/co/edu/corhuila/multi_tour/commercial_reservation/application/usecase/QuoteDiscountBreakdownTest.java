package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.BreakdownLine;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-02 of HU-COM-004: RN-01, RN-02, AC-04.1 to AC-04.3, AC-04.5. Accumulation (HU-COM-003 DP-04) is not modelled.
class QuoteDiscountBreakdownTest {

    private static final UUID TENANT = new UUID(0, 0xc1), SERVICE = new UUID(1, 1);
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate REF = LocalDate.of(2026, 6, 15);
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1), TO = LocalDate.of(2026, 12, 31);

    private final DiscountRuleFakes fakes = new DiscountRuleFakes();
    private final QuoteDiscountBreakdownUseCase quote =
            new QuoteDiscountBreakdownUseCase(new EvaluateApplicableDiscountRulesUseCase(fakes));
    private long seq = 100;

    private DiscountRule rule(String name, int priority, DiscountBase base, Integer bp, Long cents, Long cap) {
        return rule(name, priority, FROM, TO, base, bp, cents, cap);
    }

    private DiscountRule rule(String name, int priority, LocalDate from, LocalDate to, DiscountBase base, Integer bp, Long cents, Long cap) {
        DiscountRule rule = DiscountRule.create(new UUID(2, seq++), TENANT, name, from, to, priority, base, bp, cents,
                ServiceCoverage.all(), cap, CREATED);
        fakes.save(rule);
        return rule;
    }

    private DiscountBreakdown quoteOf(long cents) { return quote.quote(TENANT, SERVICE, REF, new Cents(cents)); }

    @Test
    void AC_04_1_aDiscountInForceReducesTheValueAndKeepsTheBaseVisible() {
        rule("Early", 1, DiscountBase.ORIGINAL_VALUE, 1000, null, null);
        DiscountBreakdown result = quoteOf(100_000);
        assertThat(result.baseValue()).isEqualTo(new Cents(100_000));
        assertThat(result.finalValue()).isEqualTo(new Cents(90_000));
        assertThat(result.totalDiscounted()).isEqualTo(new Cents(10_000));
        assertThat(result.lines()).containsExactly(
                new BreakdownLine(1, new UUID(2, 100), "Early", DiscountBase.ORIGINAL_VALUE, new Cents(100_000), new Cents(10_000), new Cents(90_000)));
    }

    @Test
    void AC_04_2_expiredUpcomingAndInactiveRulesLeaveOnlyTheBaseValue() {
        rule("Expired", 1, FROM, LocalDate.of(2026, 6, 14), DiscountBase.ORIGINAL_VALUE, 1000, null, null);
        rule("Upcoming", 2, LocalDate.of(2026, 6, 16), TO, DiscountBase.ORIGINAL_VALUE, 1000, null, null);
        rule("Inactive", 3, DiscountBase.ORIGINAL_VALUE, 1000, null, null).inactivate(new UUID(0, 9), CREATED.plusSeconds(60));
        DiscountBreakdown result = quoteOf(100_000);
        assertThat(result.lines()).isEmpty();
        assertThat(result.finalValue()).isEqualTo(new Cents(100_000));
        assertThat(result.totalDiscounted()).isEqualTo(new Cents(0));
    }

    @Test
    void AC_04_2_noRulesAtAllLeaveOnlyTheBaseValue() {
        DiscountBreakdown result = quoteOf(50_000);
        assertThat(result.baseValue()).isEqualTo(new Cents(50_000));
        assertThat(result.lines()).isEmpty();
        assertThat(result.finalValue()).isEqualTo(new Cents(50_000));
    }

    @Test
    void RN_02_AC_04_3_rulesApplyByPriorityEachOnItsDeclaredBase() {
        rule("Second", 2, DiscountBase.PREVIOUS_SUBTOTAL, 1000, null, null);
        rule("First", 1, DiscountBase.ORIGINAL_VALUE, 2000, null, null);
        rule("Third", 3, DiscountBase.ORIGINAL_VALUE, null, 5_000L, null);
        DiscountBreakdown result = quoteOf(100_000);
        assertThat(result.lines()).containsExactly(
                new BreakdownLine(1, new UUID(2, 101), "First", DiscountBase.ORIGINAL_VALUE, new Cents(100_000), new Cents(20_000), new Cents(80_000)),
                new BreakdownLine(2, new UUID(2, 100), "Second", DiscountBase.PREVIOUS_SUBTOTAL, new Cents(80_000), new Cents(8_000), new Cents(72_000)),
                new BreakdownLine(3, new UUID(2, 102), "Third", DiscountBase.ORIGINAL_VALUE, new Cents(100_000), new Cents(5_000), new Cents(67_000)));
        assertThat(result.finalValue()).isEqualTo(new Cents(67_000));
        assertThat(result.totalDiscounted()).isEqualTo(new Cents(33_000));
    }

    @Test
    void AC_04_5_percentageRoundsHalfUpToWholeCentsAndAFixedAmountOverTheBaseTakesOnlyTheBase() {
        rule("Pct", 1, DiscountBase.ORIGINAL_VALUE, 333, null, null);
        rule("Fixed", 2, DiscountBase.PREVIOUS_SUBTOTAL, null, 1_000_000L, null);
        DiscountBreakdown result = quoteOf(1_005);
        assertThat(result.lines().get(0).amount()).isEqualTo(new Cents(33));
        assertThat(result.lines().get(1).amount()).isEqualTo(new Cents(972));
        assertThat(result.finalValue()).isEqualTo(new Cents(0));
    }

    @Test
    void AC_04_5_anOriginalValueRuleNeverTakesMoreThanWhatIsLeft() {
        rule("Big", 1, DiscountBase.ORIGINAL_VALUE, 9000, null, null);
        rule("More", 2, DiscountBase.ORIGINAL_VALUE, 5000, null, null);
        DiscountBreakdown result = quoteOf(10_000);
        assertThat(result.lines().get(1).amount()).isEqualTo(new Cents(1_000));
        assertThat(result.lines().get(1).subtotal()).isEqualTo(new Cents(0));
        assertThat(result.finalValue()).isEqualTo(new Cents(0));
    }

    @Test
    void AC_04_5_theCapOfARuleLimitsOnlyThatRulesDiscount() {
        rule("Capped", 1, DiscountBase.ORIGINAL_VALUE, 5000, null, 10_000L);
        rule("Free", 2, DiscountBase.PREVIOUS_SUBTOTAL, 1000, null, null);
        DiscountBreakdown result = quoteOf(100_000);
        assertThat(result.lines().get(0).amount()).isEqualTo(new Cents(10_000));
        assertThat(result.lines().get(1).amount()).isEqualTo(new Cents(9_000));
        assertThat(result.finalValue()).isEqualTo(new Cents(81_000));
    }

    @Test
    void AC_04_5_theSameInputsGiveTheSameBreakdown() {
        rule("A", 1, DiscountBase.ORIGINAL_VALUE, 1234, null, null);
        rule("B", 2, DiscountBase.PREVIOUS_SUBTOTAL, null, 777L, null);
        assertThat(quoteOf(99_999)).isEqualTo(quoteOf(99_999));
        assertThat(quoteOf(99_999).lines()).hasSize(2);
    }

    @Test
    void DP_07_aTieInPriorityIsReportedAndBothRulesStillApplyInTheTechnicalOrder() {
        rule("One", 1, DiscountBase.ORIGINAL_VALUE, 1000, null, null);
        rule("Two", 1, DiscountBase.PREVIOUS_SUBTOTAL, 1000, null, null);
        DiscountBreakdown result = quoteOf(100_000);
        assertThat(result.tiedPriorities()).isEqualTo(Set.of(1));
        assertThat(result.lines()).extracting(BreakdownLine::ruleName).containsExactly("One", "Two");
    }

    // DD-8 of HU-COM-007: every line names the rule it came from, so the reservation can fix it without matching by name.
    @Test
    void DD_8_everyLineCarriesTheIdOfItsRuleEvenWhenTwoRulesShareAName() {
        DiscountRule first = rule("Same", 1, DiscountBase.ORIGINAL_VALUE, 1000, null, null);
        DiscountRule second = rule("Same", 2, DiscountBase.PREVIOUS_SUBTOTAL, 1000, null, null);
        assertThat(quoteOf(100_000).lines()).extracting(BreakdownLine::ruleId).containsExactly(first.ruleId(), second.ruleId());
    }
}
