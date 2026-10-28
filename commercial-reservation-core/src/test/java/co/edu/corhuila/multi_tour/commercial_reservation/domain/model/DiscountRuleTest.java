package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiscountRuleTest {
    private static final UUID ID = new UUID(0, 1), TENANT = new UUID(0, 2), ACTOR = new UUID(0, 3);
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1), TO = LocalDate.of(2026, 11, 30);

    private static final ServiceCoverage ALL = ServiceCoverage.all();

    private static DiscountRule percentage(Integer basisPoints) {
        return DiscountRule.create(ID, TENANT, "Low season", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, basisPoints, null, ALL, null, NOW);
    }
    private static DiscountRule amount(Long cents) {
        return DiscountRule.create(ID, TENANT, "Group", FROM, TO, 2, DiscountBase.PREVIOUS_SUBTOTAL, null, cents, ALL, null, NOW);
    }
    private static void assertRejectedOn(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.field()).isEqualTo(field));
    }

    // RN-02 (confirmed part), AC-03.1
    @Test void createsAnActivePercentageRuleWithItsConfirmedFields() {
        var rule = percentage(1250);
        assertThat(rule.ruleId()).isEqualTo(ID);
        assertThat(rule.tenantId()).isEqualTo(TENANT);
        assertThat(rule.name()).isEqualTo("Low season");
        assertThat(rule.validFrom()).isEqualTo(FROM);
        assertThat(rule.validTo()).isEqualTo(TO);
        assertThat(rule.priority()).isEqualTo(1);
        assertThat(rule.percentageBasisPoints()).isEqualTo(1250);
        assertThat(rule.amountCents()).isNull();
        assertThat(rule.status()).isEqualTo(DiscountStatus.ACTIVE);
        assertThat(rule.createdAt()).isEqualTo(NOW);
        assertThat(rule.updatedAt()).isEqualTo(NOW);
    }

    @Test void createsAFixedAmountRule() {
        var rule = amount(50_000L);
        assertThat(rule.amountCents()).isEqualTo(50_000L);
        assertThat(rule.percentageBasisPoints()).isNull();
    }

    @Test void requiresIdentityNameAndPriority() {
        assertRejectedOn(() -> DiscountRule.create(null, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null, NOW), "ruleId");
        assertRejectedOn(() -> DiscountRule.create(ID, null, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null, NOW), "tenantId");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, " ", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null, NOW), "name");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, null, FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null, NOW), "name");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, null, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null, NOW), "priority");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null, null), "occurredAt");
    }

    // RN-06, AC-03.2
    @Test void theCalculationBaseIsOriginalValueOrPreviousSubtotal() {
        assertThat(DiscountBase.values()).containsExactly(DiscountBase.ORIGINAL_VALUE, DiscountBase.PREVIOUS_SUBTOTAL);
        assertThat(percentage(100).calculationBase()).isEqualTo(DiscountBase.ORIGINAL_VALUE);
        assertThat(amount(100L).calculationBase()).isEqualTo(DiscountBase.PREVIOUS_SUBTOTAL);
    }

    @Test void aMissingCalculationBaseIsRejected() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, null, 100, null, ALL, null, NOW), "calculationBase");
    }

    // RN-08, AC-03.3
    @ParameterizedTest @ValueSource(ints = {1, 1250, 10_000})
    void acceptsAPercentageFromOneToTenThousandBasisPoints(int basisPoints) {
        assertThat(percentage(basisPoints).percentageBasisPoints()).isEqualTo(basisPoints);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 10_001})
    void rejectsAPercentageOutsideOneToTenThousandBasisPoints(int basisPoints) {
        assertRejectedOn(() -> percentage(basisPoints), "percentageBasisPoints");
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0})
    void rejectsAnAmountThatIsNotAboveZero(long cents) {
        assertRejectedOn(() -> amount(cents), "amountCents");
    }

    @Test void rejectsBothAPercentageAndAnAmount() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, 100L, ALL, null, NOW), "amountCents");
    }

    @Test void rejectsNeitherAPercentageNorAnAmount() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, null, null, ALL, null, NOW), "percentageBasisPoints");
    }

    // RN-03, AC-03.4
    @Test void rejectsAValidityWhoseStartIsAfterItsEnd() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", TO, FROM, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null, NOW), "validFrom");
    }

    @Test void rejectsAValidityWithNoEndOrNoStart() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, null, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null, NOW), "validTo");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", null, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null, NOW), "validFrom");
    }

    // DP-02: calendar dates, inclusive at both ends, so one day is a valid window.
    @Test void validityIsCalendarDatesAndASingleDayIsValid() {
        var rule = DiscountRule.create(ID, TENANT, "n", FROM, FROM, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null, NOW);
        assertThat(rule.validFrom()).isEqualTo(FROM);
        assertThat(rule.validTo()).isEqualTo(FROM);
        assertThat(rule.validFrom()).isInstanceOf(LocalDate.class);
    }

    // DP-01: all services of the tenant, or a non-empty selection; exactly one.
    @Test void aRuleCoversAllServicesOrASelectedSet() {
        var a = new UUID(1, 1);
        var b = new UUID(1, 2);
        assertThat(percentage(100).serviceCoverage().isAll()).isTrue();
        assertThat(percentage(100).serviceCoverage().serviceIds()).isEmpty();
        var selected = DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null,
                ServiceCoverage.of(Set.of(a, b)), null, NOW);
        assertThat(selected.serviceCoverage().isAll()).isFalse();
        assertThat(selected.serviceCoverage().serviceIds()).containsExactlyInAnyOrder(a, b);
    }

    @Test void aSelectionIsNeverEmptyNorHoldsANullService() {
        assertRejectedOn(() -> ServiceCoverage.of(Set.of()), "serviceCoverage");
        assertRejectedOn(() -> ServiceCoverage.of(null), "serviceCoverage");
        assertRejectedOn(() -> ServiceCoverage.of(new java.util.HashSet<>(java.util.Arrays.asList(new UUID[] {null}))),
                "serviceCoverage");
    }

    @Test void theCoverageIsRequired() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, null, null, NOW),
                "serviceCoverage");
    }

    @Test void theSelectionCannotBeChangedAfterwards() {
        var ids = new java.util.HashSet<>(Set.of(new UUID(1, 1)));
        var coverage = ServiceCoverage.of(ids);
        ids.add(new UUID(1, 2));
        assertThat(coverage.serviceIds()).hasSize(1);
        assertThatThrownBy(() -> coverage.serviceIds().add(new UUID(1, 3))).isInstanceOf(UnsupportedOperationException.class);
    }

    // DP-05: the cap is optional; when present it is a positive amount in cents.
    @Test void theCapIsOptionalAndKeptWhenGiven() {
        assertThat(percentage(100).capCents()).isNull();
        var capped = DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, 250_000L, NOW);
        assertThat(capped.capCents()).isEqualTo(250_000L);
    }

    @Test void aCapMustBeAboveZero() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, 0L, NOW), "capCents");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, -1L, NOW), "capCents");
    }

    @Test void restoreKeepsTheCoverageAndTheCap() {
        var coverage = ServiceCoverage.of(Set.of(new UUID(1, 1)));
        var rule = DiscountRule.restore(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, coverage, 500L,
                DiscountStatus.ACTIVE, NOW, NOW);
        assertThat(rule.serviceCoverage()).isEqualTo(coverage);
        assertThat(rule.capCents()).isEqualTo(500L);
    }

    // RN-09 (state half), RN-13, AC-03.9: inactivation is a transition, not a deletion.
    @Test void inactivationIsATransitionThatRemovesTheRuleFromService() {
        var rule = percentage(100);
        var later = NOW.plusSeconds(60);
        rule.inactivate(ACTOR, later);
        assertThat(rule.status()).isEqualTo(DiscountStatus.INACTIVE);
        assertThat(rule.isActive()).isFalse();
        assertThat(rule.updatedAt()).isEqualTo(later);
        assertThat(rule.createdAt()).isEqualTo(NOW);
    }

    @Test void aRepeatedInactivationDoesNotAdvanceTheAuditTime() {
        var rule = percentage(100);
        rule.inactivate(ACTOR, NOW.plusSeconds(60));
        rule.inactivate(ACTOR, NOW.plusSeconds(120));
        assertThat(rule.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test void inactivationRequiresAnActorAndATime() {
        assertRejectedOn(() -> percentage(100).inactivate(null, NOW), "actorId");
        assertRejectedOn(() -> percentage(100).inactivate(ACTOR, null), "occurredAt");
    }

    @Test void restoreKeepsAnInactiveStatus() {
        var rule = DiscountRule.restore(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, ALL, null,
                DiscountStatus.INACTIVE, NOW, NOW);
        assertThat(rule.isActive()).isFalse();
    }

    // DP-07: reactivation is not built.
    @Test void thereIsNoReactivation() {
        assertThat(DiscountRule.class.getMethods()).extracting(Method::getName)
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).matches("^(reactivate|activate).*"));
    }
}
