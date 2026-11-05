package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

// HU-COM-016 RN-01 to RN-04, RN-07, RN-14; plan D-02, D-03: one authorized refund and its execution. Amount, authorization and execution are three facts:
// the amount comes from the decision, only the Administrator authorizes (or Multitour determines it), and the refund is executed only when Cash gave the
// movement. States go forward (AUTHORIZED, EXECUTING, EXECUTED) with the single step back of the undo; the overdue mark is not a state. Immutable.
public record RefundAuthorization(UUID id, UUID tenantId, UUID reservationId, RefundOrigin origin, UUID financialDecisionId, Cents potentialRefundValue,
        Cents authorizedAmount, String reason, AuthorizationMode mode, UUID authorizedBy, Instant authorizedAt, Instant executionDueAt, ExecutionState state,
        Instant executingAt, UUID executedBy, OutflowMethod outflowMethod, Instant executedAt, UUID cashMovementId, Instant overdueMarkedAt) {

    public static final int MAX_REASON = 500;

    public RefundAuthorization {
        if (id == null || tenantId == null || reservationId == null || origin == null || potentialRefundValue == null || authorizedAmount == null
                || mode == null || authorizedAt == null || executionDueAt == null || state == null)
            throw new DomainException("refundAuthorization", "the refund data is incomplete");
        if (reason == null || reason.isBlank()) throw new DomainException("reason", "reason is required");
        if (authorizedAmount.amount() <= 0 || authorizedAmount.amount() > potentialRefundValue.amount())
            throw new DomainException("authorizedAmount", "the authorized amount must be above zero and not above the potential value");
    }

    // RN-02: the Administrator authorizes a decision whose outcome is the refund (or the authorization of a credit request).
    public static RefundAuthorization authorize(UUID id, FinancialDecision decision, String reason, UUID administrator, Instant at, Instant dueAt) {
        if (decision.outcome() != DecisionOutcome.REFUND_FULL && decision.outcome() != DecisionOutcome.AUTHORIZED)
            throw new BusinessRuleViolation("the decision has no refund outcome to authorize");
        if (administrator == null) throw new DomainException("authorizedBy", "the person is required");
        return born(id, decision, cleanReason(reason), AuthorizationMode.TENANT_ADMINISTRATOR, administrator, at, dueAt);
    }

    // AC-16.8: the refund that the 24-hour default produced counts as authorized by Multitour.
    public static RefundAuthorization authorizeByDefault(UUID id, FinancialDecision decision, Instant at, Instant dueAt) {
        if (!decision.decidedBySystem() || decision.outcome() == null || decision.outcome() == DecisionOutcome.REJECTED)
            throw new BusinessRuleViolation("only a decision defaulted by the system is authorized by default");
        return born(id, decision, "Refunded in full after 24 running hours without a decision", AuthorizationMode.AUTOMATIC_DETERMINATION, null, at, dueAt);
    }

    // RN-05: a cause not attributable to the customer (and a personal emergency above zero) is refunded in full and authorized automatically.
    public static RefundAuthorization determine(UUID id, UUID tenantId, UUID reservationId, RefundOrigin origin, Cents amount, Instant at, Instant dueAt) {
        if (origin != RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER && origin != RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION)
            throw new BusinessRuleViolation("only a cause not attributable to the customer is determined without a decision");
        return new RefundAuthorization(id, tenantId, reservationId, origin, null, amount, amount, "Determined by Multitour: " + origin,
                AuthorizationMode.AUTOMATIC_DETERMINATION, null, at, dueAt, ExecutionState.AUTHORIZED, null, null, null, null, null, null);
    }

    // RN-03: step 1 of the saga. A repeat with the same person and method changes nothing.
    public RefundAuthorization startExecution(UUID by, OutflowMethod method, Instant at) {
        if (by == null || method == null) throw new DomainException("executedBy", "the person and the method are required");
        if (state == ExecutionState.EXECUTING && by.equals(executedBy) && method == outflowMethod) return this;
        if (state != ExecutionState.AUTHORIZED) throw new InvalidStatusTransitionException("the refund cannot start from " + state);
        return with(ExecutionState.EXECUTING, at, by, method, null, null, overdueMarkedAt);
    }

    // RN-04: the compensation of step 1 when the cash step failed.
    public RefundAuthorization undoStart() {
        if (state == ExecutionState.AUTHORIZED) return this;
        if (state == ExecutionState.EXECUTED) throw new InvalidStatusTransitionException("an executed refund cannot be undone");
        return with(ExecutionState.AUTHORIZED, null, null, null, null, null, overdueMarkedAt);
    }

    // RN-04, RN-14: step 3. Never executed without the identifier Cash gave; executed is terminal.
    public RefundAuthorization complete(UUID movementId, Instant at) {
        if (state == ExecutionState.EXECUTED && Objects.equals(movementId, cashMovementId) && movementId != null) return this;
        if (movementId == null) throw new DomainException("cashMovementId", "the cash movement is required");
        if (state != ExecutionState.EXECUTING) throw new InvalidStatusTransitionException("the refund cannot complete from " + state);
        return with(ExecutionState.EXECUTED, executingAt, executedBy, outflowMethod, at, movementId, overdueMarkedAt);
    }

    public boolean isOverdue(Instant now) {
        return state != ExecutionState.EXECUTED && !now.isBefore(executionDueAt);
    }

    // RN-07: a mark, set once; the refund stays authorized and pending.
    public RefundAuthorization markOverdue(Instant now) {
        if (overdueMarkedAt != null) return this;
        if (!isOverdue(now)) throw new BusinessRuleViolation("the refund is not overdue");
        return with(state, executingAt, executedBy, outflowMethod, executedAt, cashMovementId, now);
    }

    private static RefundAuthorization born(UUID id, FinancialDecision decision, String reason, AuthorizationMode mode, UUID by, Instant at, Instant dueAt) {
        return new RefundAuthorization(id, decision.tenantId(), decision.reservationId(), RefundOrigin.valueOf(decision.kind().name()), decision.id(),
                decision.baseAmount(), decision.baseAmount(), reason, mode, by, at, dueAt, ExecutionState.AUTHORIZED, null, null, null, null, null, null);
    }

    private static String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) throw new DomainException("reason", "reason is required");
        String text = reason.strip();
        if (text.length() > MAX_REASON) throw new DomainException("reason", "reason is longer than " + MAX_REASON + " characters");
        return text;
    }

    private RefundAuthorization with(ExecutionState next, Instant startedAt, UUID by, OutflowMethod method, Instant doneAt, UUID movement, Instant overdue) {
        return new RefundAuthorization(id, tenantId, reservationId, origin, financialDecisionId, potentialRefundValue, authorizedAmount, reason, mode,
                authorizedBy, authorizedAt, executionDueAt, next, startedAt, by, method, doneAt, movement, overdue);
    }
}
