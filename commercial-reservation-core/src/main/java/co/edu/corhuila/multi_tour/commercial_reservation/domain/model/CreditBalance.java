package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// HU-COM-017 RN-01, RN-09; plan D-02: a credit balance, the customer's inside the tenant. It is born from a decision that ended as the 100 % credit or from the
// excess of a validated payment, never from a refund. What is available is derived (recorded minus applied minus set aside by live refund requests), never stored,
// and the credit has no expiry. It references the reservation and the decision or the attempt by identifier only. Immutable.
public record CreditBalance(UUID id, UUID tenantId, UUID sourceReservationId, CreditOrigin origin, Cents amount, UUID sourceDecisionId, UUID sourceAttemptId,
        Instant recordedAt, UUID recordedBy, Cents applied, Cents setAside) {

    public CreditBalance {
        if (id == null || tenantId == null || sourceReservationId == null || origin == null || amount == null || recordedAt == null || recordedBy == null
                || applied == null || setAside == null)
            throw new DomainException("creditBalance", "the credit balance data is incomplete");
        if (amount.amount() <= 0) throw new DomainException("amount", "the credit balance must be above zero");
        if ((origin == CreditOrigin.PAYMENT_EXCESS) != (sourceAttemptId != null) || (sourceDecisionId != null) == (sourceAttemptId != null))
            throw new DomainException("source", "a credit balance comes from one decision or from one payment attempt");
        if (applied.amount() + setAside.amount() > amount.amount())
            throw new BusinessRuleViolation("the credit balance applied and set aside cannot exceed the recorded amount");
    }

    // RN-02: the decision ended as the 100 % credit; the credit is the base amount of the decision, never typed.
    public static CreditBalance ofDecision(UUID id, FinancialDecision decision, UUID by, Instant at) {
        if (decision.outcome() != DecisionOutcome.CREDIT_FULL) throw new BusinessRuleViolation("the decision did not end as a credit balance");
        return new CreditBalance(id, decision.tenantId(), decision.reservationId(), CreditOrigin.valueOf(decision.kind().name()), decision.baseAmount(),
                decision.id(), null, at, by, new Cents(0), new Cents(0));
    }

    // RN-03: the excess of a validated payment is recorded as a credit of the customer and is not applied by itself.
    public static CreditBalance ofExcess(UUID id, UUID tenantId, UUID reservationId, UUID attemptId, Cents excess, UUID by, Instant at) {
        return new CreditBalance(id, tenantId, reservationId, CreditOrigin.PAYMENT_EXCESS, excess, null, attemptId, at, by, new Cents(0), new Cents(0));
    }

    public Cents available() {
        return new Cents(amount.amount() - applied.amount() - setAside.amount());
    }

    // More value applied to a reservation, on top of what was applied before; the constructor refuses a sum above the recorded amount.
    public CreditBalance withApplied(Cents more) {
        return new CreditBalance(id, tenantId, sourceReservationId, origin, amount, sourceDecisionId, sourceAttemptId, recordedAt, recordedBy, new Cents(applied.amount() + more.amount()), setAside);
    }

    // More value set aside by a live refund request.
    public CreditBalance withSetAside(Cents more) {
        return new CreditBalance(id, tenantId, sourceReservationId, origin, amount, sourceDecisionId, sourceAttemptId, recordedAt, recordedBy, applied, new Cents(setAside.amount() + more.amount()));
    }
}
