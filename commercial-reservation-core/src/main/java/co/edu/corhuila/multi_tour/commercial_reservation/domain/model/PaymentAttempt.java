package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// A payment attempt, a child of Reservation (plan D-02). A transfer is born IN_VALIDATION; cash has no validation status
// because it is money received (DP-01). The snapshot is the situation of the reservation when it was registered. The review
// columns (decision, verified, applied, excess, treatment) are HU-COM-010's and are set once by a decision (RN-09).
public final class PaymentAttempt {
    public static final Duration REVIEW_WINDOW = Duration.ofHours(24);

    private final UUID attemptId, registeredBy;
    private final PaymentMethod method;
    private final Cents amount;
    private final ValidationStatus validationStatus;
    private final PaymentStatus paymentStatusSnapshot;
    private final Instant registeredAt;
    private final UUID decidedBy;
    private final Instant decidedAt;
    private final String decisionReason;
    private final Cents verifiedAmount, appliedAmount, excessAmount, treatmentAmount;

    public PaymentAttempt(UUID attemptId, PaymentMethod method, Cents amount, ValidationStatus validationStatus,
            PaymentStatus paymentStatusSnapshot, UUID registeredBy, Instant registeredAt) {
        this(attemptId, method, amount, validationStatus, paymentStatusSnapshot, registeredBy, registeredAt, null, null, null, null,
                null, null, null);
    }

    public PaymentAttempt(UUID attemptId, PaymentMethod method, Cents amount, ValidationStatus validationStatus,
            PaymentStatus paymentStatusSnapshot, UUID registeredBy, Instant registeredAt, UUID decidedBy, Instant decidedAt,
            String decisionReason, Cents verifiedAmount, Cents appliedAmount, Cents excessAmount, Cents treatmentAmount) {
        if (attemptId == null || method == null || amount == null || paymentStatusSnapshot == null || registeredBy == null
                || registeredAt == null) throw new DomainException("paymentAttempt", "the payment attempt data is incomplete");
        if ((method == PaymentMethod.TRANSFER) == (validationStatus == null))
            throw new DomainException("validationStatus", "only a transfer has a validation status");
        this.attemptId = attemptId; this.method = method; this.amount = amount; this.validationStatus = validationStatus;
        this.paymentStatusSnapshot = paymentStatusSnapshot; this.registeredBy = registeredBy; this.registeredAt = registeredAt;
        this.decidedBy = decidedBy; this.decidedAt = decidedAt; this.decisionReason = decisionReason;
        this.verifiedAmount = verifiedAmount; this.appliedAmount = appliedAmount; this.excessAmount = excessAmount;
        this.treatmentAmount = treatmentAmount;
    }

    PaymentAttempt approvedWith(UUID reviewer, Instant now, String reason, Cents verified, Cents applied, Cents excess, Cents treatment) {
        return new PaymentAttempt(attemptId, method, amount, ValidationStatus.APPROVED, paymentStatusSnapshot, registeredBy,
                registeredAt, reviewer, now, reason, verified, applied, excess, treatment);
    }

    PaymentAttempt rejectedWith(UUID reviewer, Instant now, String reason) {
        return new PaymentAttempt(attemptId, method, amount, ValidationStatus.REJECTED, paymentStatusSnapshot, registeredBy,
                registeredAt, reviewer, now, reason, null, null, null, null);
    }

    // AC-10.6: the mark is derived from the presentation time and the clock; it is not a state and nothing stores it.
    public Instant reviewDueAt() { return registeredAt.plus(REVIEW_WINDOW); }

    public boolean reviewOverdue(Instant now) {
        return validationStatus == ValidationStatus.IN_VALIDATION && !now.isBefore(reviewDueAt());
    }

    public UUID attemptId() { return attemptId; }
    public PaymentMethod method() { return method; }
    public Cents amount() { return amount; }
    public ValidationStatus validationStatus() { return validationStatus; }
    public PaymentStatus paymentStatusSnapshot() { return paymentStatusSnapshot; }
    public UUID registeredBy() { return registeredBy; }
    public Instant registeredAt() { return registeredAt; }
    public UUID decidedBy() { return decidedBy; }
    public Instant decidedAt() { return decidedAt; }
    public String decisionReason() { return decisionReason; }
    public Cents verifiedAmount() { return verifiedAmount; }
    public Cents appliedAmount() { return appliedAmount; }
    public Cents excessAmount() { return excessAmount; }
    public Cents treatmentAmount() { return treatmentAmount; }
}
