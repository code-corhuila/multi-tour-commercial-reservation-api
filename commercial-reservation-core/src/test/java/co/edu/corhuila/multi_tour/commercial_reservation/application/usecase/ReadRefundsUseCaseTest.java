package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-06 of HU-COM-016 (-api): AC-16.13, AC-16.14; DP-04. Only the Administrator and the Collaborator read decisions and refunds, the role is judged before any
// query, a record of another tenant looks missing, and the lists of job 2 take the tenant of the path and the instant of the clock (the job asks, the service decides).
class ReadRefundsUseCaseTest {

    private final RefundFakes fakes = new RefundFakes();
    private final List<String> calls = new ArrayList<>();
    private final RefundQueries queries = new RefundQueries() {
        @Override public RefundPage<FinancialDecision> decisions(UUID tenantId, DecisionKind kind, boolean pending, boolean attention, int page, int limit) {
            calls.add("decisions " + tenantId + " " + kind + " " + pending + " " + attention + " " + page + "/" + limit);
            return new RefundPage<>(List.of(), 0);
        }
        @Override public RefundPage<FinancialDecision> dueDecisions(UUID tenantId, Instant now, int page, int limit) {
            calls.add("dueDecisions " + tenantId + " " + now);
            return new RefundPage<>(List.of(), 0);
        }
        @Override public RefundPage<RefundAuthorization> refunds(UUID tenantId, ExecutionState state, boolean overdue, Instant now, UUID decisionId, int page, int limit) {
            calls.add("refunds " + tenantId + " " + state + " " + overdue + " " + now + " " + decisionId + " " + page + "/" + limit);
            return new RefundPage<>(List.of(), 0);
        }
        @Override public RefundPage<RefundAuthorization> dueRefunds(UUID tenantId, Instant now, int page, int limit) {
            calls.add("dueRefunds " + tenantId + " " + now);
            return new RefundPage<>(List.of(), 0);
        }
    };
    private final ReadRefundsUseCase read = new ReadRefundsUseCase(queries, fakes.decisionRepository, fakes.refundRepository, RefundFakes.CLOCK);

    @Test
    void theAdministratorAndTheCollaboratorReadAndEveryOtherRoleIsRefusedBeforeAnyQuery() {
        read.decisions(RefundFakes.actor(Role.TENANT_ADMINISTRATOR), DecisionKind.CUSTOMER_CANCELLATION, true, false, 1, 20);
        read.refunds(RefundFakes.actor(Role.OPERATIONAL_COLLABORATOR), ExecutionState.EXECUTING, false, null, 2, 10);

        assertThat(calls).hasSize(2);
        for (Role other : new Role[] {Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER}) {
            assertThatThrownBy(() -> read.decisions(RefundFakes.actor(other), null, false, false, 1, 20)).isInstanceOf(PaymentAccessDeniedException.class);
            assertThatThrownBy(() -> read.refunds(RefundFakes.actor(other), null, false, null, 1, 20)).isInstanceOf(PaymentAccessDeniedException.class);
            assertThatThrownBy(() -> read.decision(RefundFakes.actor(other), UUID.randomUUID())).isInstanceOf(PaymentAccessDeniedException.class);
        }
        assertThat(calls).hasSize(2);
    }

    @Test
    void theFiltersTravelAsTheyCameAndTheTenantIsTheOneOfTheToken() {
        var admin = RefundFakes.actor(Role.TENANT_ADMINISTRATOR);
        UUID decision = UUID.randomUUID();

        read.decisions(admin, DecisionKind.PENDING_TREATMENT, true, true, 3, 5);
        read.refunds(admin, ExecutionState.AUTHORIZED, true, decision, 1, 20);

        assertThat(calls).containsExactly("decisions " + RefundFakes.TENANT + " PENDING_TREATMENT true true 3/5",
                "refunds " + RefundFakes.TENANT + " AUTHORIZED true " + RefundFakes.NOW + " " + decision + " 1/20");
    }

    @Test
    void oneDecisionOrOneRefundIsReadWholeAndOneOfAnotherTenantLooksMissing() {
        FinancialDecision decision = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        RefundAuthorization refund = RefundAuthorization.determine(UUID.randomUUID(), RefundFakes.TENANT, UUID.randomUUID(),
                co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, new Cents(1_000), RefundFakes.NOW, RefundFakes.NOW);
        fakes.refunds.put(refund.id(), refund);
        var collaborator = RefundFakes.actor(Role.OPERATIONAL_COLLABORATOR);
        var stranger = new co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor(UUID.randomUUID(), UUID.randomUUID(),
                Role.TENANT_ADMINISTRATOR);

        assertThat(read.decision(collaborator, decision.id())).isEqualTo(decision);
        assertThat(read.refund(collaborator, refund.id())).isEqualTo(refund);
        assertThatThrownBy(() -> read.decision(stranger, decision.id())).isInstanceOf(RefundNotFoundException.class);
        assertThatThrownBy(() -> read.refund(stranger, refund.id())).isInstanceOf(RefundNotFoundException.class);
    }

    @Test
    void theListsOfTheJobTakeTheTenantOfThePathAndTheInstantOfTheClock() {
        UUID tenant = UUID.randomUUID();

        read.dueDecisions(tenant, 1, 50);
        read.dueRefunds(tenant, 2, 10);

        assertThat(calls).containsExactly("dueDecisions " + tenant + " " + RefundFakes.NOW, "dueRefunds " + tenant + " " + RefundFakes.NOW);
    }
}
