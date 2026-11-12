package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizationMode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// HU-COM-016 DP-01 (Project Lead, 2026-11-12), minimal form: the decision "refund 100 %" is also the authorization. The refund and its RefundAuthorized event are written in the
// same transaction as the decision; the reason is optional; the authorize route stays and answers the refund that exists (old clients, rows decided before this change, credit requests).
class RefundDecisionOneActTest {

    private static final String DEFAULT_REASON = "Refund of 100 % decided by the Tenant Administrator";

    private final RefundFakes fakes = new RefundFakes();
    private final ManageRefundDecisionsUseCase manage = new ManageRefundDecisionsUseCase(fakes.decisionRepository, fakes.refundRepository, fakes.calendar, fakes.outbox,
            fakes.ids, fakes.transaction, RefundFakes.CLOCK);
    private final PaymentActor admin = RefundFakes.actor(Role.TENANT_ADMINISTRATOR);

    @Test
    void theDecisionCreatesTheAuthorizedRefundAndItsEventInTheSameTransaction() {
        Instant recognized = RefundFakes.NOW.minus(Duration.ofHours(3));
        FinancialDecision open = fakes.openAt(DecisionKind.CUSTOMER_CANCELLATION, 80_000, recognized);

        FinancialDecision decided = manage.recordRefundDecision(admin, open.id(), "  The customer cancelled in time ");

        assertThat(fakes.refunds).hasSize(1);
        RefundAuthorization refund = fakes.refunds.values().iterator().next();
        assertThat(refund.financialDecisionId()).isEqualTo(decided.id());
        assertThat(refund.authorizedAmount()).isEqualTo(new Cents(80_000));
        assertThat(refund.mode()).isEqualTo(AuthorizationMode.TENANT_ADMINISTRATOR);
        assertThat(refund.authorizedBy()).isEqualTo(admin.actorId());
        assertThat(refund.authorizedAt()).isEqualTo(RefundFakes.NOW);
        assertThat(refund.executionDueAt()).as("DP-02: counted from the determination").isEqualTo(recognized.plus(Duration.ofDays(5)));
        assertThat(refund.state()).isEqualTo(ExecutionState.AUTHORIZED);
        assertThat(refund.reason()).isEqualTo("The customer cancelled in time");
        assertThat(fakes.events).containsExactly(refund);
        assertThat(fakes.transactions).as("one transaction for the decision, the refund and the event").isEqualTo(1);
    }

    @Test
    void theReasonIsOptionalAndAFixedTextTakesItsPlace() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);

        manage.recordRefundDecision(admin, open.id());

        assertThat(fakes.refunds.values()).extracting(RefundAuthorization::reason).containsExactly(DEFAULT_REASON);
    }

    @Test
    void aCreditBalanceRequestIsNotRefundedInOneActAndNothingIsWritten() {
        FinancialDecision request = fakes.open(DecisionKind.CREDIT_BALANCE_REFUND_REQUEST, 50_000);

        assertThatThrownBy(() -> manage.recordRefundDecision(admin, request.id())).isInstanceOf(BusinessRuleViolation.class);

        assertThat(fakes.refunds).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void authorizingADecisionRecordedBeforeThisChangeCreatesItsRefundOnce() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        fakes.decisions.put(open.id(), open.recordRefundFull(admin.actorId(), RefundFakes.NOW));

        RefundAuthorization refund = manage.authorize(admin, open.id(), "Reason of the old client");

        assertThat(refund.reason()).isEqualTo("Reason of the old client");
        assertThat(manage.authorize(admin, open.id(), "Again")).isEqualTo(refund);
        assertThat(fakes.events).containsExactly(refund);
    }

    @Test
    void recordingAgainAnswersTheDecisionAndWritesNoSecondRefund() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        manage.recordRefundDecision(admin, open.id());

        assertThatThrownBy(() -> manage.recordRefundDecision(admin, open.id())).isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(fakes.refunds).hasSize(1);
        assertThat(fakes.events).hasSize(1);
    }

    @Test
    void onlyTheAdministratorDecidesAndNoRefundIsWrittenForAnotherRole() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);

        assertThatThrownBy(() -> manage.recordRefundDecision(RefundFakes.actor(Role.OPERATIONAL_COLLABORATOR), open.id())).isInstanceOf(PaymentAccessDeniedException.class);

        assertThat(fakes.refunds).isEmpty();
    }
}
