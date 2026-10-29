package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03: RN-04 / AC-03.6, RN-09 / AC-03.9, RN-10 (evaluation half) / AC-03.10, RNF-03, DP-08 (ties accepted, reported).
class EvaluateApplicableDiscountRulesTest {

    private static final UUID TENANT = new UUID(0, 0xb1), OTHER_TENANT = new UUID(0, 0xb2);
    private static final UUID SERVICE = new UUID(1, 1), OTHER_SERVICE = new UUID(1, 2);
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate REF = LocalDate.of(2026, 6, 15);
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1), TO = LocalDate.of(2026, 12, 31);

    private final DiscountRuleFakes fakes = new DiscountRuleFakes();
    private final EvaluateApplicableDiscountRulesUseCase evaluation = new EvaluateApplicableDiscountRulesUseCase(fakes);
    private long seq = 100;

    private DiscountRule rule(UUID tenant, int priority, LocalDate from, LocalDate to, ServiceCoverage coverage, Instant created) {
        DiscountRule rule = DiscountRule.create(new UUID(2, seq++), tenant, "rule", from, to, priority,
                DiscountBase.values()[0], 1000, null, coverage, 5_000L, created);
        fakes.save(rule);
        return rule;
    }

    private DiscountRule applicable(int priority) {
        return rule(TENANT, priority, FROM, TO, ServiceCoverage.all(), CREATED);
    }

    private ApplicableDiscountRules evaluate() { return evaluation.evaluate(TENANT, SERVICE, REF); }

    @Test
    void AC_03_6_rulesComeBackOrderedByPriorityLowerFirst() {
        DiscountRule third = applicable(3), first = applicable(1), second = applicable(2);
        assertThat(evaluate().rules()).containsExactly(first, second, third);
    }

    @Test
    void AC_03_9_expiredAndNotYetValidRulesAreNotApplicable() {
        DiscountRule kept = applicable(1);
        rule(TENANT, 2, FROM, LocalDate.of(2026, 6, 14), ServiceCoverage.all(), CREATED);
        rule(TENANT, 3, LocalDate.of(2026, 6, 16), TO, ServiceCoverage.all(), CREATED);
        assertThat(evaluate().rules()).containsExactly(kept);
    }

    @Test
    void AC_03_9_validityIsInclusiveAtBothEnds() {
        DiscountRule starts = rule(TENANT, 1, REF, TO, ServiceCoverage.all(), CREATED);
        DiscountRule ends = rule(TENANT, 2, FROM, REF, ServiceCoverage.all(), CREATED);
        assertThat(evaluate().rules()).containsExactly(starts, ends);
    }

    @Test
    void AC_03_9_inactivatedRuleIsNotApplicable() {
        DiscountRule kept = applicable(1), gone = applicable(2);
        gone.inactivate(new UUID(0, 9), CREATED.plusSeconds(60));
        assertThat(evaluate().rules()).containsExactly(kept);
    }

    @Test
    void AC_03_10_ruleCreatedAfterTheReferenceDateIsNotApplicable() {
        DiscountRule old = applicable(1);
        rule(TENANT, 2, FROM, TO, ServiceCoverage.all(), Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(evaluate().rules()).containsExactly(old);
    }

    @Test
    void AC_03_10_ruleCreatedDuringTheReferenceDateIsApplicable() {
        DiscountRule sameDay = rule(TENANT, 1, FROM, TO, ServiceCoverage.all(), Instant.parse("2026-06-15T23:59:59Z"));
        assertThat(evaluate().rules()).containsExactly(sameDay);
    }

    @Test
    void RN_04_onlyRulesThatCoverTheServiceAreApplicable() {
        DiscountRule all = applicable(3);
        DiscountRule selected = rule(TENANT, 1, FROM, TO, ServiceCoverage.of(Set.of(SERVICE, OTHER_SERVICE)), CREATED);
        rule(TENANT, 2, FROM, TO, ServiceCoverage.of(Set.of(OTHER_SERVICE)), CREATED);
        assertThat(evaluate().rules()).containsExactly(selected, all);
    }

    @Test
    void RN_04_otherTenantRulesAreNeverReturned() {
        DiscountRule mine = applicable(1);
        rule(OTHER_TENANT, 1, FROM, TO, ServiceCoverage.all(), CREATED);
        assertThat(evaluate().rules()).containsExactly(mine);
    }

    @Test
    void RNF_03_sameInputsGiveTheSameResultTwice() {
        applicable(2); applicable(1); applicable(3);
        assertThat(evaluate()).isEqualTo(evaluate());
    }

    @Test
    void DP_08_tiesAreAcceptedOrderedByCreationThenIdAndReported() {
        DiscountRule later = rule(TENANT, 1, FROM, TO, ServiceCoverage.all(), CREATED.plusSeconds(60));
        DiscountRule earlierA = rule(TENANT, 1, FROM, TO, ServiceCoverage.all(), CREATED);
        DiscountRule earlierB = rule(TENANT, 1, FROM, TO, ServiceCoverage.all(), CREATED);
        DiscountRule lowest = applicable(2);
        ApplicableDiscountRules result = evaluate();
        assertThat(result.rules()).containsExactly(earlierA, earlierB, later, lowest);
        assertThat(result.tiedPriorities()).containsExactly(1);
    }

    @Test
    void DP_08_noTieReportsNothing() {
        applicable(1); applicable(2);
        assertThat(evaluate().tiedPriorities()).isEmpty();
    }

    @Test
    void capIsKeptOnTheRuleButNotAppliedByTheEvaluation() {
        applicable(1);
        assertThat(evaluate().rules().get(0).capCents()).isEqualTo(5_000L);
    }
}
