package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangePaymentParametersCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentParameterOption;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParametersChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParametersChange;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameterValues;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-08 of HU-COM-009, the core part: RN-14, RN-13; AC-09.17. Only the last change of each row is kept (DP-04, DD-5); the audit trace is the event PaymentParametersChanged (DP-04, 2026-11-12); the list of earlier
// changes is served by the audit domain. A change reaches new reservations only because each reservation
// stores its own deadline: nothing here touches a reservation.
class ManagePaymentParametersTest {

    private static final UUID TENANT = new UUID(9, 0xf1), OTHER_TENANT = new UUID(9, 0xf2), ADMIN = new UUID(9, 0xf3);
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private final List<String> journal = new ArrayList<>();
    private final List<PaymentParameter> rows = new ArrayList<>();
    private final PaymentParameterRepository repository = new PaymentParameterRepository() {
        @Override public List<PaymentParameter> findByTenant(UUID tenantId) {
            return tenantId.equals(TENANT) ? List.copyOf(rows) : List.of();
        }
        @Override public void save(UUID tenantId, PaymentParameter parameter) {
            journal.add("save " + tenantId);
            rows.removeIf(row -> row.values().paymentType() == parameter.values().paymentType()
                    && java.util.Objects.equals(row.values().serviceId(), parameter.values().serviceId()));
            rows.add(parameter);
        }
    };
    private final List<PaymentParametersChange> events = new ArrayList<>();
    private boolean outboxFails;
    private final PaymentParametersChangeOutbox outbox = change -> {
        if (outboxFails) throw new IllegalStateException("the outbox is down");
        journal.add("event " + change.paymentType());
        events.add(change);
    };
    private final ManagePaymentParametersUseCase manage = new ManagePaymentParametersUseCase(repository, new ImmediateTransaction(journal), outbox,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static PaymentActor actor(Role role) {
        return new PaymentActor(TENANT, ADMIN, role);
    }

    private static PaymentParameterValues deposit(int bp) {
        return new PaymentParameterValues(PaymentType.DEPOSIT, null, 24, 24, bp, 48, true, true);
    }

    @Test
    void parametersNeedAnAdministratorABoundedValueAndAReason() {
        for (Role other : List.of(Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER)) {
            assertThatThrownBy(() -> manage.change(new ChangePaymentParametersCommand(actor(other), "new season", List.of(deposit(3_000)))))
                    .isInstanceOf(PaymentAccessDeniedException.class);
        }
        assertThatThrownBy(() -> manage.change(new ChangePaymentParametersCommand(actor(Role.TENANT_ADMINISTRATOR), " ", List.of(deposit(3_000)))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("reason"));
        assertThatThrownBy(() -> manage.change(new ChangePaymentParametersCommand(actor(Role.TENANT_ADMINISTRATOR), "new season", List.of())))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("parameters"));

        assertThat(rows).isEmpty();
        assertThat(events).isEmpty();
        assertThat(journal).isEmpty();
    }

    @Test
    void theAdministratorChangesTheValuesAndTheRowKeepsTheAuthorTheReasonAndTheTime() {
        List<PaymentParameter> changed = manage.change(new ChangePaymentParametersCommand(actor(Role.TENANT_ADMINISTRATOR), "new season",
                List.of(deposit(3_000))));

        assertThat(changed).hasSize(1);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.changedBy()).isEqualTo(ADMIN);
            assertThat(row.changeReason()).isEqualTo("new season");
            assertThat(row.changedAt()).isEqualTo(NOW);
            assertThat(row.values().minimumDepositBp()).isEqualTo(3_000);
        });
        assertThat(journal).containsExactly("begin", "save " + TENANT, "event DEPOSIT", "commit");
    }

    @Test
    void aSecondChangeReplacesTheRowAndKeepsOnlyTheLastOne() {
        manage.change(new ChangePaymentParametersCommand(actor(Role.TENANT_ADMINISTRATOR), "first", List.of(deposit(3_000))));
        manage.change(new ChangePaymentParametersCommand(actor(Role.TENANT_ADMINISTRATOR), "second", List.of(deposit(4_000))));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.changeReason()).isEqualTo("second");
            assertThat(row.values().minimumDepositBp()).isEqualTo(4_000);
        });
    }

    @Test
    void everyRoleOfTheMatrixReadsTheParametersOfItsOwnTenantOnly() {
        manage.change(new ChangePaymentParametersCommand(actor(Role.TENANT_ADMINISTRATOR), "new season", List.of(deposit(3_000))));

        for (Role reader : List.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ANALYST, Role.ACCOUNTANT)) {
            assertThat(manage.read(actor(reader))).hasSize(1);
        }
        assertThat(manage.read(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR))).isEmpty();
        assertThatThrownBy(() -> manage.read(actor(Role.FINAL_CUSTOMER))).isInstanceOf(PaymentAccessDeniedException.class);
    }

    @Test
    void theFinalCustomerReadsOnlyTheTypeTheServiceAndTheTwoEnabledMethodsOfItsOwnTenant() {
        UUID service = new UUID(9, 0xf4);
        manage.change(new ChangePaymentParametersCommand(actor(Role.TENANT_ADMINISTRATOR), "new season",
                List.of(deposit(3_000), new PaymentParameterValues(PaymentType.FULL_PAYMENT, service, 12, 36, null, null, true, false))));

        assertThat(manage.readOptions(actor(Role.FINAL_CUSTOMER))).containsExactlyInAnyOrder(
                new PaymentParameterOption(PaymentType.DEPOSIT, null, true, true),
                new PaymentParameterOption(PaymentType.FULL_PAYMENT, service, true, false));
        assertThat(manage.readOptions(new PaymentActor(OTHER_TENANT, ADMIN, Role.FINAL_CUSTOMER))).isEmpty();
        for (Role reader : List.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ANALYST, Role.ACCOUNTANT)) {
            assertThat(manage.readOptions(actor(reader))).hasSize(2);
        }
        assertThatThrownBy(() -> manage.readOptions(new PaymentActor(null, ADMIN, Role.FINAL_CUSTOMER))).isInstanceOf(PaymentAccessDeniedException.class);
    }

    @Test
    void eachChangedRowWritesOnePaymentParametersChangedEventInTheSameTransactionAsTheChange() {
        UUID service = new UUID(9, 0xf4);
        manage.change(new ChangePaymentParametersCommand(actor(Role.TENANT_ADMINISTRATOR), "new season",
                List.of(deposit(3_000), new PaymentParameterValues(PaymentType.FULL_PAYMENT, service, 12, 36, null, null, true, false))));

        assertThat(events).containsExactly(
                new PaymentParametersChange(TENANT, ADMIN, PaymentType.DEPOSIT, null, NOW),
                new PaymentParametersChange(TENANT, ADMIN, PaymentType.FULL_PAYMENT, service, NOW));
        assertThat(journal).containsExactly("begin", "save " + TENANT, "event DEPOSIT", "save " + TENANT, "event FULL_PAYMENT", "commit");
    }

    @Test
    void theEventNamesTheTenantTheAuthorTheParameterAndTheInstantAndCarriesNoValueAndNoReason() {
        assertThat(Arrays.stream(PaymentParametersChange.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList())
                .containsExactly("tenantId", "actorId", "paymentType", "serviceId", "changedAt");
    }

    @Test
    void aChangeWhoseEventCannotBeWrittenDoesNotCommit() {
        outboxFails = true;

        assertThatThrownBy(() -> manage.change(new ChangePaymentParametersCommand(actor(Role.TENANT_ADMINISTRATOR), "new season", List.of(deposit(3_000)))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(journal).containsExactly("begin", "save " + TENANT);
    }

    private record ImmediateTransaction(List<String> journal) implements co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction {
        @Override
        public <T> T inTransaction(java.util.function.Supplier<T> work) {
            journal.add("begin");
            T result = work.get();
            journal.add("commit");
            return result;
        }
    }
}
