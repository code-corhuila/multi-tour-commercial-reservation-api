package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiscountRuleTest {
    private static final UUID ID = new UUID(0, 1), TENANT = new UUID(0, 2), ACTOR = new UUID(0, 3);
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z"), TO = Instant.parse("2026-11-30T00:00:00Z");

    private static DiscountRule percentage(Integer basisPoints) {
        return DiscountRule.create(ID, TENANT, "Low season", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, basisPoints, null, NOW);
    }
    private static DiscountRule amount(Long cents) {
        return DiscountRule.create(ID, TENANT, "Group", FROM, TO, 2, DiscountBase.PREVIOUS_SUBTOTAL, null, cents, NOW);
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
        assertRejectedOn(() -> DiscountRule.create(null, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, NOW), "ruleId");
        assertRejectedOn(() -> DiscountRule.create(ID, null, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, NOW), "tenantId");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, " ", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, NOW), "name");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, null, FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, NOW), "name");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, null, DiscountBase.ORIGINAL_VALUE, 100, null, NOW), "priority");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, null), "occurredAt");
    }

    // RN-06, AC-03.2
    @Test void theCalculationBaseIsOriginalValueOrPreviousSubtotal() {
        assertThat(DiscountBase.values()).containsExactly(DiscountBase.ORIGINAL_VALUE, DiscountBase.PREVIOUS_SUBTOTAL);
        assertThat(percentage(100).calculationBase()).isEqualTo(DiscountBase.ORIGINAL_VALUE);
        assertThat(amount(100L).calculationBase()).isEqualTo(DiscountBase.PREVIOUS_SUBTOTAL);
    }

    @Test void aMissingCalculationBaseIsRejected() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, null, 100, null, NOW), "calculationBase");
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
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, 100L, NOW), "amountCents");
    }

    @Test void rejectsNeitherAPercentageNorAnAmount() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, null, null, NOW), "percentageBasisPoints");
    }

    // RN-03, AC-03.4
    @Test void rejectsAValidityWhoseStartIsAfterItsEnd() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", TO, FROM, 1, DiscountBase.ORIGINAL_VALUE, 100, null, NOW), "validFrom");
    }

    @Test void rejectsAValidityWithNoEndOrNoStart() {
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", FROM, null, 1, DiscountBase.ORIGINAL_VALUE, 100, null, NOW), "validTo");
        assertRejectedOn(() -> DiscountRule.create(ID, TENANT, "n", null, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null, NOW), "validFrom");
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
        var rule = DiscountRule.restore(ID, TENANT, "n", FROM, TO, 1, DiscountBase.ORIGINAL_VALUE, 100, null,
                DiscountStatus.INACTIVE, NOW, NOW);
        assertThat(rule.isActive()).isFalse();
    }

    // DP-07: reactivation is not built.
    @Test void thereIsNoReactivation() {
        assertThat(DiscountRule.class.getMethods()).extracting(Method::getName)
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).matches("^(reactivate|activate).*"));
    }
}
