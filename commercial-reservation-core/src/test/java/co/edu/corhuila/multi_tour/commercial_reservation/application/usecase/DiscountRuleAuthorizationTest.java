package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.ACTOR;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// RN-01, AC-03.5: all or nothing (plan D-08). TODO DP-09: reading the list is open and is not covered.
class DiscountRuleAuthorizationTest {
    private final DiscountRuleFakes fakes = new DiscountRuleFakes();
    private final CreateDiscountRuleUseCase create =
            new CreateDiscountRuleUseCase(fakes, fakes, fakes, fakes.ids, fakes.clock);
    private final InactivateDiscountRuleUseCase inactivate =
            new InactivateDiscountRuleUseCase(fakes, fakes, fakes, fakes.clock);
    private final CreateDiscountRule.Command command = new CreateDiscountRule.Command("Low season",
            Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-30T00:00:00Z"), 1,
            DiscountBase.ORIGINAL_VALUE, 1250, null);

    @Test void theTenantAdministratorCreatesAndInactivates() {
        var admin = actor(Role.TENANT_ADMINISTRATOR);
        var rule = create.create(admin, command);
        assertThat(rule).isNotNull();
        assertThat(inactivate.inactivate(admin, rule.ruleId()).isActive()).isFalse();
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = "TENANT_ADMINISTRATOR", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherRoleIsRefusedOnCreateAsAPermissionMatter(Role role) {
        assertThatThrownBy(() -> create.create(actor(role), command))
                .isInstanceOf(DiscountRuleAccessDeniedException.class).isNotInstanceOf(DomainException.class);
        assertThat(fakes.rules).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = "TENANT_ADMINISTRATOR", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherRoleIsRefusedOnInactivateEvenForAnExistingRule(Role role) {
        var rule = create.create(actor(Role.TENANT_ADMINISTRATOR), command);
        assertThat(rule).isNotNull();
        fakes.events.clear();
        assertThatThrownBy(() -> inactivate.inactivate(actor(role), rule.ruleId()))
                .isInstanceOf(DiscountRuleAccessDeniedException.class).isNotInstanceOf(DomainException.class);
        assertThat(rule.isActive()).isTrue();
        assertThat(fakes.events).isEmpty();
    }

    @Test void aMissingOrIncompleteActorIsRefused() {
        assertThatThrownBy(() -> DiscountRuleAuthorization.requireWrite(null))
                .isInstanceOf(DiscountRuleAccessDeniedException.class);
        assertThatThrownBy(() -> DiscountRuleAuthorization.requireWrite(
                new DiscountRuleActor(null, ACTOR, Role.TENANT_ADMINISTRATOR)))
                .isInstanceOf(DiscountRuleAccessDeniedException.class);
        assertThatThrownBy(() -> DiscountRuleAuthorization.requireWrite(new DiscountRuleActor(TENANT, ACTOR, null)))
                .isInstanceOf(DiscountRuleAccessDeniedException.class);
    }
}
