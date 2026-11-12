package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.OutflowMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-016 DP-16 (Project Lead, 2026-11-12): with the tenant inactive the decisions of 24 hours are closed (record, authorize, reject and the default); reading refunds and
// executing the refunds already determined stay open. The mechanism is the one of HU-COM-020: the suspension reader (open since), as in the decision of the proofs.
class InactiveTenantRefundsTest {

    private final RefundFakes fakes = new RefundFakes();
    private Instant suspendedSince = RefundFakes.NOW.minus(Duration.ofHours(1));
    private final ManageRefundDecisionsUseCase manage = new ManageRefundDecisionsUseCase(fakes.decisionRepository, fakes.refundRepository, fakes.calendar, fakes.outbox,
            fakes.ids, fakes.transaction, RefundFakes.CLOCK, tenantId -> Optional.ofNullable(suspendedSince));
    private final RefundAutomationUseCase automation = new RefundAutomationUseCase(fakes.decisionRepository, fakes.refundRepository, fakes.calendar, fakes.outbox, fakes.ids,
            fakes.transaction, Clock.fixed(RefundFakes.NOW.plus(Duration.ofHours(25)), ZoneOffset.UTC), tenantId -> Optional.ofNullable(suspendedSince));
    private final PaymentActor admin = RefundFakes.actor(Role.TENANT_ADMINISTRATOR);

    @Test
    void anInactiveTenantRecordsNoRefundDecision() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);

        assertThatThrownBy(() -> manage.recordRefundDecision(admin, open.id())).isInstanceOf(BusinessRuleViolation.class);

        assertThat(fakes.decisions.get(open.id()).outcome()).isNull();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void anInactiveTenantAuthorizesNothingNotEvenADecisionAlreadyRecorded() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        suspendedSince = null;
        manage.recordRefundDecision(admin, open.id());
        suspendedSince = RefundFakes.NOW.minus(Duration.ofHours(1));

        assertThatThrownBy(() -> manage.authorize(admin, open.id(), "Reason")).isInstanceOf(BusinessRuleViolation.class);

        assertThat(fakes.refunds).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void anInactiveTenantRejectsNoCreditBalanceRequest() {
        FinancialDecision request = fakes.open(DecisionKind.CREDIT_BALANCE_REFUND_REQUEST, 50_000);

        assertThatThrownBy(() -> manage.reject(admin, request.id(), "Not valid")).isInstanceOf(BusinessRuleViolation.class);

        assertThat(fakes.decisions.get(request.id()).outcome()).isNull();
    }

    @Test
    void theDefaultOf24HoursDoesNotRunForAnInactiveTenant() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);

        assertThatThrownBy(() -> automation.applyDefault(RefundFakes.TENANT, open.id())).isInstanceOf(BusinessRuleViolation.class);

        assertThat(fakes.decisions.get(open.id()).outcome()).isNull();
        assertThat(fakes.refunds).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void theDecisionsWorkAgainWhenTheTenantIsActive() {
        suspendedSince = null;
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        FinancialDecision late = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 10_000);

        assertThat(manage.recordRefundDecision(admin, open.id()).outcome()).isNotNull();
        assertThat(manage.authorize(admin, open.id(), "Reason")).isNotNull();
        assertThat(automation.applyDefault(RefundFakes.TENANT, late.id())).isNotNull();
    }

    @Test
    void executingARefundAlreadyDeterminedStaysOpenForAnInactiveTenant() {
        RefundAuthorization refund = RefundAuthorization.determine(fakes.ids.newId(), RefundFakes.TENANT, UUID.randomUUID(), RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER,
                new Cents(100_000), RefundFakes.NOW, RefundFakes.NOW.plusSeconds(3_600));
        fakes.refunds.put(refund.id(), refund);
        var execute = new ExecuteRefundUseCase(fakes.refundRepository, fakes.transaction, RefundFakes.CLOCK);

        assertThat(execute.start(RefundFakes.TENANT, refund.id(), UUID.randomUUID(), OutflowMethod.TRANSFER).state()).isEqualTo(ExecutionState.EXECUTING);
        assertThat(execute.complete(RefundFakes.TENANT, refund.id(), UUID.randomUUID()).state()).isEqualTo(ExecutionState.EXECUTED);
    }
}
