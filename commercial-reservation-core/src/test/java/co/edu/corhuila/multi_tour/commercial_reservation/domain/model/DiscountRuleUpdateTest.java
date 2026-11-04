package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-003 decision 2026-11-03: stackable (DP-04), update (AC-03.16) and reactivation (AC-03.17) of the aggregate.
class DiscountRuleUpdateTest {
    private static final UUID ID = new UUID(0, 1), TENANT = new UUID(0, 2), ACTOR = new UUID(0, 3);
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z"), LATER = NOW.plusSeconds(60);
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1), TO = LocalDate.of(2026, 11, 30);

    private static DiscountRule rule() {
        return DiscountRule.create(ID, TENANT, "Low season", FROM, TO, 2, DiscountBase.ORIGINAL_VALUE, 1250, null,
                ServiceCoverage.all(), 90_000L, NOW);
    }
    private static void assertRejectedOn(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.field()).isEqualTo(field));
    }

    @Test void aRuleIsStackableUnlessItSaysOtherwise() {
        assertThat(rule().stackable()).isTrue();
        var stops = DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null,
                ServiceCoverage.all(), null, false, NOW);
        assertThat(stops.stackable()).isFalse();
    }

    @Test void restoreKeepsTheFlagAndAnOldRowReadsTrue() {
        var stored = DiscountRule.restore(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null,
                ServiceCoverage.all(), null, false, DiscountStatus.ACTIVE, NOW, NOW);
        assertThat(stored.stackable()).isFalse();
        var legacy = DiscountRule.restore(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null,
                ServiceCoverage.all(), null, DiscountStatus.ACTIVE, NOW, NOW);
        assertThat(legacy.stackable()).isTrue();
    }

    @Test void AC_03_16_onlyTheFieldsSentChange() {
        var rule = rule();
        var changed = rule.update(ACTOR, DiscountRulePatch.none().withName("High season").withStackable(false), LATER);
        assertThat(changed).isTrue();
        assertThat(rule.name()).isEqualTo("High season");
        assertThat(rule.stackable()).isFalse();
        assertThat(rule.validFrom()).isEqualTo(FROM);
        assertThat(rule.validTo()).isEqualTo(TO);
        assertThat(rule.priority()).isEqualTo(2);
        assertThat(rule.calculationBase()).isEqualTo(DiscountBase.ORIGINAL_VALUE);
        assertThat(rule.percentageBasisPoints()).isEqualTo(1250);
        assertThat(rule.capCents()).isEqualTo(90_000L);
        assertThat(rule.updatedAt()).isEqualTo(LATER);
        assertThat(rule.createdAt()).isEqualTo(NOW);
        assertThat(rule.ruleId()).isEqualTo(ID);
        assertThat(rule.tenantId()).isEqualTo(TENANT);
        assertThat(rule.status()).isEqualTo(DiscountStatus.ACTIVE);
    }

    @Test void AC_03_16_aBodyThatChangesNothingIsNotAChange() {
        var rule = rule();
        assertThat(rule.update(ACTOR, DiscountRulePatch.none(), LATER)).isFalse();
        assertThat(rule.update(ACTOR, DiscountRulePatch.none().withName("Low season").withPriority(2)
                .withCapCents(90_000L).withServiceCoverage(ServiceCoverage.all()), LATER)).isFalse();
        assertThat(rule.updatedAt()).isEqualTo(NOW);
    }

    @Test void AC_03_16_aNullCapRemovesTheCapAndAnAbsentOneKeepsIt() {
        var rule = rule();
        assertThat(rule.update(ACTOR, DiscountRulePatch.none().withPriority(3), LATER)).isTrue();
        assertThat(rule.capCents()).isEqualTo(90_000L);
        assertThat(rule.update(ACTOR, DiscountRulePatch.none().withCapCents(null), LATER)).isTrue();
        assertThat(rule.capCents()).isNull();
    }

    @Test void AC_03_16_switchingTheValueKindClearsTheOtherOne() {
        var rule = rule();
        rule.update(ACTOR, DiscountRulePatch.none().withAmountCents(50_000L), LATER);
        assertThat(rule.amountCents()).isEqualTo(50_000L);
        assertThat(rule.percentageBasisPoints()).isNull();
        rule.update(ACTOR, DiscountRulePatch.none().withPercentageBasisPoints(500), LATER.plusSeconds(1));
        assertThat(rule.percentageBasisPoints()).isEqualTo(500);
        assertThat(rule.amountCents()).isNull();
    }

    @Test void AC_03_16_coverageAndCalculationBaseCanChange() {
        var rule = rule();
        var service = new UUID(1, 1);
        rule.update(ACTOR, DiscountRulePatch.none().withServiceCoverage(ServiceCoverage.of(Set.of(service)))
                .withCalculationBase(DiscountBase.PREVIOUS_SUBTOTAL), LATER);
        assertThat(rule.serviceCoverage().serviceIds()).containsExactly(service);
        assertThat(rule.calculationBase()).isEqualTo(DiscountBase.PREVIOUS_SUBTOTAL);
    }

    @Test void AC_03_16_theMergedRuleMustBeValidAsACreationAndAFailureChangesNothing() {
        var rule = rule();
        assertRejectedOn(() -> rule.update(ACTOR, DiscountRulePatch.none().withValidFrom(TO.plusDays(1)), LATER), "validFrom");
        assertRejectedOn(() -> rule.update(ACTOR, DiscountRulePatch.none().withName(" "), LATER), "name");
        assertRejectedOn(() -> rule.update(ACTOR, DiscountRulePatch.none().withPriority(0), LATER), "priority");
        assertRejectedOn(() -> rule.update(ACTOR, DiscountRulePatch.none().withPercentageBasisPoints(10_001), LATER), "percentageBasisPoints");
        assertRejectedOn(() -> rule.update(ACTOR, DiscountRulePatch.none().withAmountCents(100L).withPercentageBasisPoints(100), LATER), "amountCents");
        assertRejectedOn(() -> rule.update(ACTOR, DiscountRulePatch.none().withCapCents(0L), LATER), "capCents");
        assertThat(rule.name()).isEqualTo("Low season");
        assertThat(rule.validFrom()).isEqualTo(FROM);
        assertThat(rule.percentageBasisPoints()).isEqualTo(1250);
        assertThat(rule.updatedAt()).isEqualTo(NOW);
    }

    @Test void AC_03_16_aRequiredFieldCannotBeCleared() {
        var rule = rule();
        assertRejectedOn(() -> rule.update(ACTOR, DiscountRulePatch.none().withName(null), LATER), "name");
        assertRejectedOn(() -> rule.update(ACTOR, DiscountRulePatch.none().withValidTo(null), LATER), "validTo");
    }

    @Test void updateRequiresAnActorAndATime() {
        assertRejectedOn(() -> rule().update(null, DiscountRulePatch.none(), LATER), "actorId");
        assertRejectedOn(() -> rule().update(ACTOR, DiscountRulePatch.none(), null), "occurredAt");
        assertRejectedOn(() -> rule().update(ACTOR, null, LATER), "patch");
    }

    @Test void AC_03_16_anInactiveRuleCanBeUpdatedAndStaysInactive() {
        var rule = rule();
        rule.inactivate(ACTOR, LATER);
        assertThat(rule.update(ACTOR, DiscountRulePatch.none().withPriority(5), LATER.plusSeconds(1))).isTrue();
        assertThat(rule.status()).isEqualTo(DiscountStatus.INACTIVE);
    }

    @Test void AC_03_17_reactivationMirrorsInactivation() {
        var rule = rule();
        rule.inactivate(ACTOR, LATER);
        rule.reactivate(ACTOR, LATER.plusSeconds(60));
        assertThat(rule.status()).isEqualTo(DiscountStatus.ACTIVE);
        assertThat(rule.isActive()).isTrue();
        assertThat(rule.updatedAt()).isEqualTo(LATER.plusSeconds(60));
        assertThat(rule.createdAt()).isEqualTo(NOW);
    }

    @Test void AC_03_17_aRepeatedReactivationDoesNotAdvanceTheAuditTime() {
        var rule = rule();
        rule.reactivate(ACTOR, LATER);
        assertThat(rule.updatedAt()).isEqualTo(NOW);
    }

    @Test void AC_03_17_reactivationRequiresAnActorAndATime() {
        assertRejectedOn(() -> rule().reactivate(null, NOW), "actorId");
        assertRejectedOn(() -> rule().reactivate(ACTOR, null), "occurredAt");
    }
}
