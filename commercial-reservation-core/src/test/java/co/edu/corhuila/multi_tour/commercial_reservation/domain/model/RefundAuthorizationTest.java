package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-01 of HU-COM-016 (-api): RN-01 to RN-04, RN-07, RN-14; AC-16.2, AC-16.4 to AC-16.7, AC-16.11. The refund: amount, authorization and execution are
// three facts. It is born only from a decided decision (or by a determination), it never reads executed without the cash movement, the
// undo returns it to authorized, an executed refund is terminal and the overdue mark is not a state. Pure: ids, cents and instants.
class RefundAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant DUE = NOW.plus(Duration.ofDays(7));
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID COLLABORATOR = UUID.randomUUID();
    private static final UUID MOVEMENT = UUID.randomUUID();

    private static FinancialDecision decided() {
        return FinancialDecision.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DecisionKind.CUSTOMER_CANCELLATION,
                new Cents(80_000), new Cents(20_000), null, NOW).recordRefundFull(ADMIN, NOW);
    }

    private static RefundAuthorization authorized() {
        return RefundAuthorization.authorize(UUID.randomUUID(), decided(), "The customer cancelled in time", ADMIN, NOW, DUE);
    }

    @Test
    void anAuthorizationNeedsAReasonAndTheRefundOutcome() {
        FinancialDecision decision = decided();

        RefundAuthorization refund = RefundAuthorization.authorize(UUID.randomUUID(), decision, "  The customer cancelled in time ", ADMIN, NOW, DUE);

        assertThat(refund.authorizedAmount()).isEqualTo(decision.baseAmount());
        assertThat(refund.potentialRefundValue()).isEqualTo(decision.baseAmount());
        assertThat(refund.origin()).isEqualTo(RefundOrigin.CUSTOMER_CANCELLATION);
        assertThat(refund.mode()).isEqualTo(AuthorizationMode.TENANT_ADMINISTRATOR);
        assertThat(refund.authorizedBy()).isEqualTo(ADMIN);
        assertThat(refund.executionDueAt()).isEqualTo(DUE);
        assertThat(refund.state()).isEqualTo(ExecutionState.AUTHORIZED);
        assertThat(refund.reason()).isEqualTo("The customer cancelled in time");
        assertThatThrownBy(() -> RefundAuthorization.authorize(UUID.randomUUID(), decision, " ", ADMIN, NOW, DUE)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> RefundAuthorization.authorize(UUID.randomUUID(), decision, "x".repeat(501), ADMIN, NOW, DUE)).isInstanceOf(DomainException.class);
        FinancialDecision open = FinancialDecision.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DecisionKind.PENDING_TREATMENT,
                new Cents(10), null, null, NOW);
        assertThatThrownBy(() -> RefundAuthorization.authorize(UUID.randomUUID(), open, "Reason", ADMIN, NOW, DUE))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void notAttributableToTheCustomerIsRefundedInFullAndAuthorizedAutomatically() {
        RefundAuthorization refund = RefundAuthorization.determine(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, new Cents(300_000), NOW, DUE);

        assertThat(refund.mode()).isEqualTo(AuthorizationMode.AUTOMATIC_DETERMINATION);
        assertThat(refund.authorizedBy()).isNull();
        assertThat(refund.financialDecisionId()).isNull();
        assertThat(refund.authorizedAmount()).isEqualTo(new Cents(300_000));
        assertThat(refund.reason()).isNotBlank();
        assertThatThrownBy(() -> RefundAuthorization.determine(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                RefundOrigin.CUSTOMER_CANCELLATION, new Cents(1), NOW, DUE)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> RefundAuthorization.determine(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION, new Cents(0), NOW, DUE)).isInstanceOf(DomainException.class);
    }

    @Test
    void theDefaultAuthorizationCountsAsAutomaticAndKeepsTheDecision() {
        FinancialDecision defaulted = FinancialDecision.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DecisionKind.PENDING_TREATMENT,
                new Cents(40_000), null, null, NOW).applyDefault(NOW.plus(Duration.ofHours(24)));

        RefundAuthorization refund = RefundAuthorization.authorizeByDefault(UUID.randomUUID(), defaulted, NOW.plus(Duration.ofHours(24)), DUE);

        assertThat(refund.mode()).isEqualTo(AuthorizationMode.AUTOMATIC_DETERMINATION);
        assertThat(refund.authorizedBy()).isNull();
        assertThat(refund.financialDecisionId()).isEqualTo(defaulted.id());
        assertThat(refund.origin()).isEqualTo(RefundOrigin.PENDING_TREATMENT);
    }

    @Test
    void onlyAnAuthorizedRefundCanStartItsExecution() {
        RefundAuthorization started = authorized().startExecution(COLLABORATOR, OutflowMethod.TRANSFER, NOW.plusSeconds(10));

        assertThat(started.state()).isEqualTo(ExecutionState.EXECUTING);
        assertThat(started.executedBy()).isEqualTo(COLLABORATOR);
        assertThat(started.outflowMethod()).isEqualTo(OutflowMethod.TRANSFER);
        assertThat(started.executingAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(started.startExecution(COLLABORATOR, OutflowMethod.TRANSFER, NOW.plusSeconds(99))).isEqualTo(started);
        assertThatThrownBy(() -> started.startExecution(ADMIN, OutflowMethod.TRANSFER, NOW)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> started.startExecution(COLLABORATOR, OutflowMethod.CASH, NOW)).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void aRefundExecutingIsNeverExecutedWithoutACashMovement() {
        RefundAuthorization started = authorized().startExecution(COLLABORATOR, OutflowMethod.CASH, NOW);

        assertThatThrownBy(() -> started.complete(null, NOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> authorized().complete(MOVEMENT, NOW)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(started.state()).isEqualTo(ExecutionState.EXECUTING);
    }

    @Test
    void undoingTheStartLeavesTheRefundAuthorized() {
        RefundAuthorization authorized = authorized();
        RefundAuthorization undone = authorized.startExecution(COLLABORATOR, OutflowMethod.CASH, NOW).undoStart();

        assertThat(undone).isEqualTo(authorized);
        assertThat(authorized.undoStart()).isEqualTo(authorized);
        assertThatThrownBy(() -> authorized.startExecution(COLLABORATOR, OutflowMethod.CASH, NOW).complete(MOVEMENT, NOW).undoStart())
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void anExecutedRefundIsTerminalAndAnotherMovementIsRefused() {
        RefundAuthorization done = authorized().startExecution(COLLABORATOR, OutflowMethod.TRANSFER, NOW).complete(MOVEMENT, NOW.plusSeconds(30));

        assertThat(done.state()).isEqualTo(ExecutionState.EXECUTED);
        assertThat(done.cashMovementId()).isEqualTo(MOVEMENT);
        assertThat(done.executedAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(done.complete(MOVEMENT, NOW.plusSeconds(99))).isEqualTo(done);
        assertThatThrownBy(() -> done.complete(UUID.randomUUID(), NOW)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> done.startExecution(COLLABORATOR, OutflowMethod.TRANSFER, NOW)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> done.markOverdue(DUE.plusSeconds(1))).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void anOverdueMarkKeepsTheRefundPendingAndIsNotAState() {
        RefundAuthorization refund = authorized();

        assertThat(refund.isOverdue(DUE.minusSeconds(1))).isFalse();
        assertThat(refund.isOverdue(DUE)).isTrue();
        RefundAuthorization marked = refund.markOverdue(DUE);
        assertThat(marked.overdueMarkedAt()).isEqualTo(DUE);
        assertThat(marked.state()).isEqualTo(ExecutionState.AUTHORIZED);
        assertThat(marked.markOverdue(DUE.plusSeconds(60))).isEqualTo(marked);
        assertThatThrownBy(() -> refund.markOverdue(DUE.minusSeconds(1))).isInstanceOf(BusinessRuleViolation.class);
        assertThat(marked.startExecution(COLLABORATOR, OutflowMethod.CASH, DUE.plusSeconds(5)).state()).isEqualTo(ExecutionState.EXECUTING);
    }
}
