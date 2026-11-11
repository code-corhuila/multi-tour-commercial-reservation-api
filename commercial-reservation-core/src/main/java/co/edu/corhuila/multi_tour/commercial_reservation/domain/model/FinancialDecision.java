package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// HU-COM-016 RN-01, RN-02, RN-06, RN-08; plan D-02, D-04: one financial decision of 24 running hours. The outcome is recorded once with the person (or the
// system) and the instant; the amount is the base amount recognized and is never typed; a frozen decision is never defaulted; only a credit-balance refund
// request can be rejected. Immutable: every change returns a new value. The 24 hours are not parametrizable (PDR section 15).
public record FinancialDecision(UUID id, UUID tenantId, UUID reservationId, UUID creditBalanceId, DecisionKind kind, Cents baseAmount, Cents penalty,
        Instant recognizedAt, Instant frozenAt, Long remainingAtFreezeMinutes, DecisionOutcome outcome, boolean decidedBySystem, UUID decidedBy,
        Instant decidedAt, String rejectionJustification) {

    public static final Duration WINDOW = Duration.ofHours(24);
    public static final int MAX_JUSTIFICATION = 500;

    public FinancialDecision {
        if (id == null || tenantId == null || reservationId == null || kind == null || baseAmount == null || recognizedAt == null)
            throw new DomainException("financialDecision", "the decision data is incomplete");
        if (baseAmount.amount() <= 0) throw new DomainException("baseAmount", "the base amount must be above zero");
        if (penalty != null && kind != DecisionKind.CUSTOMER_CANCELLATION)
            throw new DomainException("penalty", "only a customer cancellation carries a penalty");
        if ((creditBalanceId != null) != (kind == DecisionKind.CREDIT_BALANCE_REFUND_REQUEST))
            throw new DomainException("creditBalanceId", "the credit balance is for a refund request and only for it");
    }

    public static FinancialDecision open(UUID id, UUID tenantId, UUID reservationId, DecisionKind kind, Cents base, Cents penalty, UUID creditBalanceId,
            Instant recognizedAt) {
        return new FinancialDecision(id, tenantId, reservationId, creditBalanceId, kind, base, penalty, recognizedAt, null, null, null, false, null, null,
                null);
    }

    public boolean isDecided() {
        return outcome != null;
    }

    public boolean isFrozen() {
        return frozenAt != null;
    }

    public Instant dueAt() {
        return recognizedAt.plus(WINDOW);
    }

    // Open, not frozen, and the 24 running hours have passed.
    public boolean isDue(Instant now) {
        return !isDecided() && !isFrozen() && !now.isBefore(dueAt());
    }

    public FinancialDecision recordRefundFull(UUID by, Instant at) {
        requireOpen();
        if (kind == DecisionKind.CREDIT_BALANCE_REFUND_REQUEST)
            throw new BusinessRuleViolation("a credit-balance refund request is authorized, not refunded in full");
        if (by == null) throw new DomainException("decidedBy", "the person is required");
        return decide(DecisionOutcome.REFUND_FULL, false, by, at, null);
    }

    public FinancialDecision reject(UUID by, String justification, Instant at) {
        requireOpen();
        if (kind != DecisionKind.CREDIT_BALANCE_REFUND_REQUEST)
            throw new BusinessRuleViolation("only a credit-balance refund request can be rejected");
        if (by == null) throw new DomainException("decidedBy", "the person is required");
        if (justification == null || justification.isBlank()) throw new DomainException("justification", "justification is required");
        String text = justification.strip();
        if (text.length() > MAX_JUSTIFICATION) throw new DomainException("justification", "justification is longer than " + MAX_JUSTIFICATION + " characters");
        return decide(DecisionOutcome.REJECTED, false, by, at, text);
    }

    // RN-06: 24 running hours without a decision are the 100 % refund (or the authorization of the request), counted as authorized.
    public FinancialDecision applyDefault(Instant at) {
        requireOpen();
        if (isFrozen()) throw new BusinessRuleViolation("a frozen decision is not defaulted");
        if (at.isBefore(dueAt())) throw new BusinessRuleViolation("the 24 hours have not passed");
        DecisionOutcome result = kind == DecisionKind.CREDIT_BALANCE_REFUND_REQUEST ? DecisionOutcome.AUTHORIZED : DecisionOutcome.REFUND_FULL;
        return decide(result, true, null, at, null);
    }

    // GAP-003: a value recognized again for an open decision takes the updated amount, without a second decision.
    public FinancialDecision withLateValue(Cents increase) {
        requireOpen();
        return new FinancialDecision(id, tenantId, reservationId, creditBalanceId, kind, new Cents(baseAmount.amount() + increase.amount()), penalty,
                recognizedAt, frozenAt, remainingAtFreezeMinutes, null, false, null, null, null);
    }

    // The writer of the freeze is HU-COM-020; the decision keeps the minutes it had left.
    public FinancialDecision freeze(Instant at) {
        requireOpen();
        long left = Math.max(0, Duration.between(at, dueAt()).toMinutes());
        return new FinancialDecision(id, tenantId, reservationId, creditBalanceId, kind, baseAmount, penalty, recognizedAt, at, left, null, false, null,
                null, null);
    }

    private void requireOpen() {
        if (isDecided()) throw new InvalidStatusTransitionException("the decision is already decided");
    }

    private FinancialDecision decide(DecisionOutcome result, boolean bySystem, UUID by, Instant at, String justification) {
        return new FinancialDecision(id, tenantId, reservationId, creditBalanceId, kind, baseAmount, penalty, recognizedAt, frozenAt,
                remainingAtFreezeMinutes, result, bySystem, by, at, justification);
    }
}
