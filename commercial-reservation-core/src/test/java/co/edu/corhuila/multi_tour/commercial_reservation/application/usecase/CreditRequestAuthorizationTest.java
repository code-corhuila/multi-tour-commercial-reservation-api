package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizationMode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// HU-COM-016 DP-17 (RN-RES-009, GAP-014): the Administrator authorizes an open request to refund a credit balance inside its 24 running hours. The decision is recorded
// AUTHORIZED by the person and the refund (mode TENANT_ADMINISTRATOR, the requested amount) and its RefundAuthorized event are written in the same transaction.
class CreditRequestAuthorizationTest {

    private final RefundFakes fakes = new RefundFakes();
    private final ManageRefundDecisionsUseCase manage = new ManageRefundDecisionsUseCase(fakes.decisionRepository, fakes.refundRepository, fakes.calendar, fakes.outbox,
            fakes.ids, fakes.transaction, RefundFakes.CLOCK);
    private final PaymentActor admin = RefundFakes.actor(Role.TENANT_ADMINISTRATOR);

    @Test
    void theAdministratorAuthorizesAnOpenCreditRequestWithItsRefundAndEventInOneTransaction() {
        Instant recognized = RefundFakes.NOW.minus(Duration.ofHours(3));
        FinancialDecision request = fakes.openAt(DecisionKind.CREDIT_BALANCE_REFUND_REQUEST, 50_000, recognized);

        RefundAuthorization refund = manage.authorize(admin, request.id(), "  The customer asked for the money back ");

        FinancialDecision decided = fakes.decisions.get(request.id());
        assertThat(decided.outcome()).isEqualTo(DecisionOutcome.AUTHORIZED);
        assertThat(decided.decidedBy()).isEqualTo(admin.actorId());
        assertThat(decided.decidedBySystem()).isFalse();
        assertThat(decided.decidedAt()).isEqualTo(RefundFakes.NOW);
        assertThat(refund.financialDecisionId()).isEqualTo(request.id());
        assertThat(refund.authorizedAmount()).isEqualTo(new Cents(50_000));
        assertThat(refund.mode()).isEqualTo(AuthorizationMode.TENANT_ADMINISTRATOR);
        assertThat(refund.authorizedBy()).isEqualTo(admin.actorId());
        assertThat(refund.state()).isEqualTo(ExecutionState.AUTHORIZED);
        assertThat(refund.executionDueAt()).as("DP-02: counted from the request").isEqualTo(recognized.plus(Duration.ofDays(5)));
        assertThat(fakes.events).containsExactly(refund);
        assertThat(fakes.transactions).as("one transaction for the decision, the refund and the event").isEqualTo(1);
    }

    @Test
    void authorizingAgainAnswersTheSameRefundAndWritesNoSecondOne() {
        FinancialDecision request = fakes.open(DecisionKind.CREDIT_BALANCE_REFUND_REQUEST, 50_000);
        RefundAuthorization refund = manage.authorize(admin, request.id(), "First");

        assertThat(manage.authorize(admin, request.id(), "Second")).isEqualTo(refund);

        assertThat(fakes.refunds).hasSize(1);
        assertThat(fakes.events).hasSize(1);
    }

    @Test
    void nobodyButTheAdministratorAuthorizesARequestAndNothingChanges() {
        FinancialDecision request = fakes.open(DecisionKind.CREDIT_BALANCE_REFUND_REQUEST, 50_000);

        for (Role other : new Role[] {Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER})
            assertThatThrownBy(() -> manage.authorize(RefundFakes.actor(other), request.id(), "Reason")).isInstanceOf(PaymentAccessDeniedException.class);

        assertThat(fakes.decisions.get(request.id()).outcome()).isNull();
        assertThat(fakes.refunds).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void aRejectedRequestCannotBeAuthorizedAnymore() {
        FinancialDecision request = fakes.open(DecisionKind.CREDIT_BALANCE_REFUND_REQUEST, 50_000);
        manage.reject(admin, request.id(), "The balance is not refundable");

        assertThatThrownBy(() -> manage.authorize(admin, request.id(), "Changed my mind")).isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(fakes.decisions.get(request.id()).outcome()).isEqualTo(DecisionOutcome.REJECTED);
        assertThat(fakes.refunds).isEmpty();
    }

    @Test
    void authorizingARequestNeedsItsReasonAndWritesNothingWithoutIt() {
        FinancialDecision request = fakes.open(DecisionKind.CREDIT_BALANCE_REFUND_REQUEST, 50_000);

        assertThatThrownBy(() -> manage.authorize(admin, request.id(), "  ")).isInstanceOf(RuntimeException.class);

        assertThat(fakes.decisions.get(request.id()).outcome()).as("the decision stays open").isNull();
        assertThat(fakes.refunds).isEmpty();
    }
}
