package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.ACTOR;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.FOREIGN;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.NOW;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.ORDINARY;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.OTHER_TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.RISKY;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer.Command;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingHighRiskDataException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// HU-COM-005 T-02, T-06, T-08 at the application level: RN-01, RN-02, RN-09, RN-10, RN-12, RN-13.
// TODO(T-07/AC-05.14): Idempotency-Key is enforced over HTTP and the real schema; not at this level.
// TODO(T-05, DP-05) e-mail linking, T-03 companions (DP-02) and T-04 sensitive reads are other slices.
class RegisterLeadCustomerUseCaseTest {
    private static final HighRiskData COMPLETE = new HighRiskData("O+", "Luis 3009876543", "Ninguna", true);
    private final CustomerFakes fakes = new CustomerFakes();
    private final RegisterLeadCustomerUseCase register =
            new RegisterLeadCustomerUseCase(fakes, fakes, fakes, fakes, fakes.ids, fakes.clock);

    private static Command command(UUID service, HighRiskData data) {
        return new Command(service, " cc-123ab ", "Ana Perez", LocalDate.of(1990, 5, 17), null,
                "ana@example.com", "3001234567", true, data);
    }

    @Test void registersTheLeadCustomerAndWritesItWithItsEvent() {
        Customer lead = register.register(actor(Role.OPERATIONAL_COLLABORATOR), command(ORDINARY, null));

        assertThat(lead).isNotNull();
        assertThat(lead.customerId()).isEqualTo(fakes.ids.newId());
        assertThat(lead.tenantId()).isEqualTo(TENANT);
        assertThat(lead.documentNumber().value()).isEqualTo("CC-123AB");
        assertThat(fakes.findByTenantAndId(TENANT, lead.customerId())).contains(lead);
        assertThat(fakes.events).hasSize(1);
        assertThat(fakes.events.get(0).customerId()).isEqualTo(lead.customerId());
        assertThat(fakes.events.get(0).tenantId()).isEqualTo(TENANT);
        assertThat(fakes.events.get(0).actorId()).isEqualTo(ACTOR);
        assertThat(fakes.events.get(0).occurredAt()).isEqualTo(NOW);
    }

    @Test void highRiskActivityRefusesTheLeadWithoutTheExtraData() {
        assertThatThrownBy(() -> register.register(actor(Role.TENANT_ADMINISTRATOR), command(RISKY, null)))
                .isInstanceOfSatisfying(MissingHighRiskDataException.class,
                        e -> assertThat(e.fields()).contains("bloodType", "emergencyContact"));
        assertThat(fakes.customers).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test void highRiskActivityAcceptsTheLeadWithTheExtraData() {
        var lead = register.register(actor(Role.TENANT_ADMINISTRATOR), command(RISKY, COMPLETE));

        assertThat(lead.highRiskData()).isEqualTo(COMPLETE);
        assertThat(fakes.events).hasSize(1);
    }

    @Test void theRegisteredLeadCarriesTheRiskMarkOfItsActivity() {
        var risky = register.register(actor(Role.TENANT_ADMINISTRATOR), command(RISKY, COMPLETE));
        var ordinary = register.register(actor(Role.TENANT_ADMINISTRATOR), command(ORDINARY, null));

        assertThat(risky.highRisk()).isTrue();
        assertThat(ordinary.highRisk()).isFalse();
    }

    @Test void ordinaryActivityDoesNotRequireTheExtraData() {
        assertThat(register.register(actor(Role.TENANT_ADMINISTRATOR), command(ORDINARY, null))).isNotNull();
    }

    @Test void aFutureBirthDateIsRefusedAndNothingIsStored() {
        var future = new Command(ORDINARY, "CC-1", "Ana Perez", LocalDate.of(2026, 10, 28), null,
                "ana@example.com", null, true, null);

        assertThatThrownBy(() -> register.register(actor(Role.TENANT_ADMINISTRATOR), future))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("birthDate"));
        assertThat(fakes.customers).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"TENANT_ADMINISTRATOR", "OPERATIONAL_COLLABORATOR"},
            mode = EnumSource.Mode.EXCLUDE)
    void everyOtherRoleIsRefusedAsAPermissionMatter(Role role) {
        assertThatThrownBy(() -> register.register(actor(role), command(ORDINARY, null)))
                .isInstanceOf(CustomerAccessDeniedException.class).isNotInstanceOf(DomainException.class);
        assertThat(fakes.customers).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test void aMissingOrIncompleteActorIsRefused() {
        assertThatThrownBy(() -> register.register(null, command(ORDINARY, null)))
                .isInstanceOf(CustomerAccessDeniedException.class);
        assertThatThrownBy(() -> register.register(new CustomerActor(null, ACTOR, Role.TENANT_ADMINISTRATOR),
                command(ORDINARY, null))).isInstanceOf(CustomerAccessDeniedException.class);
    }

    @Test void aServiceOfAnotherTenantAnswersLikeAMissingOne() {
        assertThatThrownBy(() -> register.register(actor(Role.TENANT_ADMINISTRATOR), command(FOREIGN, null)))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThatThrownBy(() -> register.register(actor(Role.TENANT_ADMINISTRATOR), command(new UUID(0, 9), null)))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThat(fakes.customers).isEmpty();
    }

    @Test void theCustomerIsReadableOnlyInsideItsTenant() {
        var lead = register.register(actor(Role.TENANT_ADMINISTRATOR), command(ORDINARY, null));

        assertThat(lead).isNotNull();
        assertThat(fakes.findByTenantAndId(OTHER_TENANT, lead.customerId())).isEmpty();
        assertThat(fakes.findByTenantAndId(TENANT, lead.customerId())).isPresent();
    }

    @Test void ifTheEventCannotBeWrittenTheCustomerIsNotStored() {
        fakes.failOnAppend = new IllegalStateException("outbox down");

        assertThatThrownBy(() -> register.register(actor(Role.TENANT_ADMINISTRATOR), command(ORDINARY, null)))
                .hasMessage("outbox down");
        assertThat(fakes.customers).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test void ifTheCustomerCannotBeSavedNoEventIsWritten() {
        fakes.failOnSave = new IllegalStateException("db down");

        assertThatThrownBy(() -> register.register(actor(Role.TENANT_ADMINISTRATOR), command(ORDINARY, null)))
                .hasMessage("db down");
        assertThat(fakes.events).isEmpty();
    }

    @Test void theEventCarriesNoPersonalOrSensitiveData() {
        register.register(actor(Role.TENANT_ADMINISTRATOR), command(RISKY, COMPLETE));

        assertThat(fakes.events).hasSize(1);
        var event = fakes.events.get(0);
        assertThat(event.getClass().getRecordComponents()).extracting(c -> c.getName())
                .containsExactly("tenantId", "customerId", "actorId", "occurredAt");
        assertThat(event.toString()).doesNotContain("CC-123AB", "ana@example.com", "3001234567", "O+", "Luis");
    }
}
