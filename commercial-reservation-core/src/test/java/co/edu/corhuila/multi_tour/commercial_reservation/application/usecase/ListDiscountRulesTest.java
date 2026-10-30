package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.OTHER_TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// HU-COM-003 list (contract listDiscountRules): the tenant's rules, inactive ones included, Administrator only (AC-03.5).
class ListDiscountRulesTest {
    private final DiscountRuleFakes fakes = new DiscountRuleFakes();
    private final ListDiscountRulesUseCase list = new ListDiscountRulesUseCase(fakes);
    private long next = 1;

    private DiscountRule rule(DiscountRuleActor who, long minutesLater) {
        var create = new CreateDiscountRuleUseCase(fakes, fakes, fakes, () -> new UUID(0, next++),
                Clock.offset(fakes.clock, Duration.ofMinutes(minutesLater)));
        return create.create(who, new CreateDiscountRule.Command("Rule " + next, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), 1, DiscountBase.ORIGINAL_VALUE, 1000, null, ServiceCoverage.all(), null));
    }

    @Test void listsOnlyTheTenantRulesNewestFirstAndKeepsTheInactiveOnes() {
        var admin = actor(Role.TENANT_ADMINISTRATOR);
        var oldest = rule(admin, 0);
        var newest = rule(admin, 10);
        new InactivateDiscountRuleUseCase(fakes, fakes, fakes, fakes.clock).inactivate(admin, oldest.ruleId());
        rule(new DiscountRuleActor(OTHER_TENANT, admin.actorId(), Role.TENANT_ADMINISTRATOR), 5);

        var rules = list.list(admin);

        assertThat(rules).extracting(DiscountRule::ruleId).containsExactly(newest.ruleId(), oldest.ruleId());
        assertThat(rules).extracting(DiscountRule::isActive).containsExactly(true, false);
    }

    @Test void aTenantWithoutRulesGetsAnEmptyList() {
        assertThat(list.list(actor(Role.TENANT_ADMINISTRATOR))).isEmpty();
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = "TENANT_ADMINISTRATOR", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherRoleIsRefusedAsAPermissionMatter(Role role) {
        assertThatThrownBy(() -> list.list(actor(role))).isInstanceOf(DiscountRuleAccessDeniedException.class);
    }
}
