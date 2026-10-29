package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.ACTOR;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.NOW;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.OTHER_TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.TENANT;
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
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

// RN-10 (second half), RN-11, AC-03.11, AC-03.12; create and inactivate (update: see the TODO at the end).
class DiscountRuleChangeAuditTest {
    private final DiscountRuleFakes fakes = new DiscountRuleFakes();
    private final CreateDiscountRuleUseCase create =
            new CreateDiscountRuleUseCase(fakes, fakes, fakes, fakes.ids, fakes.clock);
    private final InactivateDiscountRuleUseCase inactivate =
            new InactivateDiscountRuleUseCase(fakes, fakes, fakes, fakes.clock);
    private final CreateDiscountRule.Command command = new CreateDiscountRule.Command("Low season",
            Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-30T00:00:00Z"), 1,
            DiscountBase.ORIGINAL_VALUE, 1250, null);
    private final DiscountRuleActor admin = actor(Role.TENANT_ADMINISTRATOR);

    @Test void creationWritesTheRuleAndOneEventWithNoPreviousAndTheFullNewState() {
        var rule = create.create(admin, command);
        assertThat(rule).isNotNull();
        assertThat(fakes.findByTenantAndId(TENANT, rule.ruleId())).contains(rule);
        assertThat(fakes.events).hasSize(1);
        var event = fakes.events.get(0);
        assertThat(event.previous()).isNull();
        assertThat(event.current()).isEqualTo(DiscountRuleState.of(rule));
        assertThat(event.current().percentageBasisPoints()).isEqualTo(1250);
        assertThat(event.tenantId()).isEqualTo(TENANT);
        assertThat(event.ruleId()).isEqualTo(rule.ruleId());
        assertThat(event.actorId()).isEqualTo(ACTOR);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test void inactivationWritesOneEventWithBothFullStates() {
        var rule = create.create(admin, command);
        assertThat(rule).isNotNull();
        var later = new InactivateDiscountRuleUseCase(fakes, fakes, fakes,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        later.inactivate(admin, rule.ruleId());
        assertThat(fakes.events).hasSize(2);
        var event = fakes.events.get(1);
        assertThat(event.previous().status()).isEqualTo(DiscountStatus.ACTIVE);
        assertThat(event.previous().updatedAt()).isEqualTo(NOW);
        assertThat(event.current().status()).isEqualTo(DiscountStatus.INACTIVE);
        assertThat(event.current().updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(event.current().name()).isEqualTo(event.previous().name());
        assertThat(event.actorId()).isEqualTo(ACTOR);
        assertThat(event.occurredAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test void aRepeatedInactivationIsNotANewFactAndPublishesNothing() {
        var rule = create.create(admin, command);
        assertThat(rule).isNotNull();
        inactivate.inactivate(admin, rule.ruleId());
        inactivate.inactivate(admin, rule.ruleId());
        assertThat(fakes.events).hasSize(2);
    }

    @Test void ifTheRuleCannotBeSavedThereIsNoEvent() {
        fakes.failOnSave = new IllegalStateException("db down");
        assertThatThrownBy(() -> create.create(admin, command)).isInstanceOf(IllegalStateException.class);
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.rules).isEmpty();
    }

    @Test void ifTheEventCannotBeAppendedTheRuleIsNotWrittenEither() {
        fakes.failOnAppend = new IllegalStateException("outbox down");
        assertThatThrownBy(() -> create.create(admin, command)).isInstanceOf(IllegalStateException.class);
        assertThat(fakes.rules).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test void aFailedInactivationLeavesNoEventForTheChange() {
        var rule = create.create(admin, command);
        assertThat(rule).isNotNull();
        fakes.failOnAppend = new IllegalStateException("outbox down");
        assertThatThrownBy(() -> inactivate.inactivate(admin, rule.ruleId())).isInstanceOf(IllegalStateException.class);
        assertThat(fakes.events).hasSize(1);
    }

    @Test void anInvalidRuleWritesNothingAndPublishesNothing() {
        var invalid = new CreateDiscountRule.Command("x", command.validTo(), command.validFrom(), 1,
                DiscountBase.ORIGINAL_VALUE, 1250, null);
        assertThatThrownBy(() -> create.create(admin, invalid)).isInstanceOf(DomainException.class);
        assertThat(fakes.rules).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test void aRuleOfAnotherTenantIsAnsweredAsMissing() {
        var rule = create.create(admin, command);
        assertThat(rule).isNotNull();
        var stranger = new DiscountRuleActor(OTHER_TENANT, ACTOR, Role.TENANT_ADMINISTRATOR);
        assertThatThrownBy(() -> inactivate.inactivate(stranger, rule.ruleId()))
                .isInstanceOf(DiscountRuleNotFoundException.class);
        assertThat(fakes.events).hasSize(1);
    }

    // RN-11, AC-03.11: nothing outside the rule is written because the slice has no port that could.
    // A new collaborator in a use case fails here until it is judged and added on purpose.
    @Test void theUseCasesDependOnlyOnThePortsThatWriteTheRuleAndItsEvent() {
        Set<Class<?>> allowed = Set.of(DiscountRuleRepository.class, DiscountRuleChangeOutbox.class,
                DiscountRuleTransaction.class, IdGenerator.class, Clock.class);
        for (Class<?> useCase : new Class<?>[] {CreateDiscountRuleUseCase.class, InactivateDiscountRuleUseCase.class}) {
            assertThat(useCase.getConstructors()).hasSize(1);
            assertThat(useCase.getConstructors()[0].getParameterTypes()).isNotEmpty().allMatch(allowed::contains);
            assertThat(useCase.getDeclaredFields()).filteredOn(f -> !Modifier.isStatic(f.getModifiers()))
                    .extracting(f -> f.getType()).isNotEmpty().allMatch(allowed::contains);
        }
    }

    // TODO update: the spec lists it (D-10, AC-03.2) but the aggregate has no update behavior and the editable
    // fields are not closed; its audit test comes with it.
}
