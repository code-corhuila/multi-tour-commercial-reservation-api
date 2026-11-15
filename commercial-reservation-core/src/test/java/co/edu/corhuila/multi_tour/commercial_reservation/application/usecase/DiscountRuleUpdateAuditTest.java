package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.ACTOR;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.NOW;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.OTHER_TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRulePatch;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// AC-03.5, AC-03.11, AC-03.12, AC-03.13, AC-03.16, AC-03.17 for the update and the reactivation.
class DiscountRuleUpdateAuditTest {
    private final DiscountRuleFakes fakes = new DiscountRuleFakes();
    private final CreateDiscountRuleUseCase create = new CreateDiscountRuleUseCase(fakes, fakes, fakes, fakes.ids, fakes.clock);
    private final Clock later = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);
    private final UpdateDiscountRuleUseCase update = new UpdateDiscountRuleUseCase(fakes, fakes, fakes, later);
    private final InactivateDiscountRuleUseCase inactivate = new InactivateDiscountRuleUseCase(fakes, fakes, fakes, later);
    private final ReactivateDiscountRuleUseCase reactivate = new ReactivateDiscountRuleUseCase(fakes, fakes, fakes, later);
    private final CreateDiscountRule.Command command = new CreateDiscountRule.Command("Low season",
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 1,
            DiscountBase.ORIGINAL_VALUE, 1250, null, ServiceCoverage.all(), null);
    private final DiscountRuleActor admin = actor(Role.TENANT_ADMINISTRATOR);

    @Test void creationWithoutTheFlagStoresTrueAndWithItStoresIt() {
        assertThat(create.create(admin, command).stackable()).isTrue();
        var no = new CreateDiscountRule.Command("x", command.validFrom(), command.validTo(), 1,
                DiscountBase.ORIGINAL_VALUE, 1250, null, ServiceCoverage.all(), null, false);
        assertThat(create.create(admin, no).stackable()).isFalse();
    }

    @Test void AC_03_16_updateWritesTheRuleAndOneEventWithBothStates() {
        var rule = create.create(admin, command);
        var updated = update.update(admin, rule.ruleId(), DiscountRulePatch.none().withName("High").withStackable(false));
        assertThat(updated.name()).isEqualTo("High");
        assertThat(fakes.events).hasSize(2);
        var event = fakes.events.get(1);
        assertThat(event.previous().name()).isEqualTo("Low season");
        assertThat(event.previous().stackable()).isTrue();
        assertThat(event.current().name()).isEqualTo("High");
        assertThat(event.current().stackable()).isFalse();
        assertThat(event.current()).isEqualTo(DiscountRuleState.of(updated));
        assertThat(event.actorId()).isEqualTo(ACTOR);
        assertThat(event.occurredAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test void AC_03_16_aBodyThatChangesNothingPublishesNothing() {
        var rule = create.create(admin, command);
        update.update(admin, rule.ruleId(), DiscountRulePatch.none().withName("Low season"));
        assertThat(fakes.events).hasSize(1);
    }

    @Test void AC_03_16_anInvalidMergeWritesNothingAndPublishesNothing() {
        var rule = create.create(admin, command);
        assertThatThrownBy(() -> update.update(admin, rule.ruleId(), DiscountRulePatch.none().withPriority(0)))
                .isInstanceOf(DomainException.class);
        assertThat(fakes.findByTenantAndId(admin.tenantId(), rule.ruleId()).orElseThrow().priority()).isEqualTo(1);
        assertThat(fakes.events).hasSize(1);
    }

    @Test void ifTheEventCannotBeAppendedTheUpdateIsNotWrittenEither() {
        var rule = create.create(admin, command);
        fakes.failOnAppend = new IllegalStateException("outbox down");
        assertThatThrownBy(() -> update.update(admin, rule.ruleId(), DiscountRulePatch.none().withName("High")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fakes.events).hasSize(1);
    }

    @Test void AC_03_17_reactivationWritesOneEventWithBothFullStates() {
        var rule = create.create(admin, command);
        inactivate.inactivate(admin, rule.ruleId());
        reactivate.reactivate(admin, rule.ruleId());
        assertThat(fakes.events).hasSize(3);
        var event = fakes.events.get(2);
        assertThat(event.previous().status()).isEqualTo(DiscountStatus.INACTIVE);
        assertThat(event.current().status()).isEqualTo(DiscountStatus.ACTIVE);
        assertThat(event.current().name()).isEqualTo(event.previous().name());
        assertThat(event.actorId()).isEqualTo(ACTOR);
    }

    @Test void AC_03_17_aRepeatedReactivationOfAnActiveRulePublishesNothing() {
        var rule = create.create(admin, command);
        assertThat(reactivate.reactivate(admin, rule.ruleId()).isActive()).isTrue();
        assertThat(fakes.events).hasSize(1);
    }

    @Test void AC_03_13_aRuleOfAnotherTenantIsAnsweredAsMissing() {
        var rule = create.create(admin, command);
        var stranger = new DiscountRuleActor(OTHER_TENANT, ACTOR, Role.TENANT_ADMINISTRATOR);
        assertThatThrownBy(() -> update.update(stranger, rule.ruleId(), DiscountRulePatch.none()))
                .isInstanceOf(DiscountRuleNotFoundException.class);
        assertThatThrownBy(() -> reactivate.reactivate(stranger, rule.ruleId()))
                .isInstanceOf(DiscountRuleNotFoundException.class);
        assertThat(fakes.events).hasSize(1);
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = "TENANT_ADMINISTRATOR", mode = EnumSource.Mode.EXCLUDE)
    void AC_03_5_everyOtherRoleIsRejectedAndNothingIsWritten(Role role) {
        var rule = create.create(admin, command);
        var other = actor(role);
        assertThatThrownBy(() -> update.update(other, rule.ruleId(), DiscountRulePatch.none().withName("x")))
                .isInstanceOf(DiscountRuleAccessDeniedException.class);
        assertThatThrownBy(() -> reactivate.reactivate(other, rule.ruleId()))
                .isInstanceOf(DiscountRuleAccessDeniedException.class);
        assertThat(fakes.events).hasSize(1);
    }

    // AC-03.11: the new use cases keep the same collaborators, so no port can write a reservation.
    @Test void AC_03_11_theNewUseCasesDependOnlyOnThePortsThatWriteTheRuleAndItsEvent() {
        Set<Class<?>> allowed = Set.of(DiscountRuleRepository.class, DiscountRuleChangeOutbox.class,
                DiscountRuleTransaction.class, Clock.class);
        for (Class<?> useCase : new Class<?>[] {UpdateDiscountRuleUseCase.class, ReactivateDiscountRuleUseCase.class}) {
            assertThat(useCase.getConstructors()).hasSize(1);
            assertThat(useCase.getConstructors()[0].getParameterTypes()).isNotEmpty().allMatch(allowed::contains);
            assertThat(useCase.getDeclaredFields()).filteredOn(f -> !Modifier.isStatic(f.getModifiers()))
                    .extracting(f -> f.getType()).isNotEmpty().allMatch(allowed::contains);
        }
    }
}
