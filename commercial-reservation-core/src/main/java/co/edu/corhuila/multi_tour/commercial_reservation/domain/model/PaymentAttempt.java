package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// A payment attempt, a child of Reservation (plan D-02). A transfer is born IN_VALIDATION; cash has no validation status
// because it is money received (DP-01). The snapshot is the situation of the reservation when it was registered.
public final class PaymentAttempt {
    private final UUID attemptId, registeredBy;
    private final PaymentMethod method;
    private final Cents amount;
    private final ValidationStatus validationStatus;
    private final PaymentStatus paymentStatusSnapshot;
    private final Instant registeredAt;

    public PaymentAttempt(UUID attemptId, PaymentMethod method, Cents amount, ValidationStatus validationStatus,
            PaymentStatus paymentStatusSnapshot, UUID registeredBy, Instant registeredAt) {
        if (attemptId == null || method == null || amount == null || paymentStatusSnapshot == null || registeredBy == null
                || registeredAt == null) throw new DomainException("paymentAttempt", "the payment attempt data is incomplete");
        if ((method == PaymentMethod.TRANSFER) == (validationStatus == null))
            throw new DomainException("validationStatus", "only a transfer has a validation status");
        this.attemptId = attemptId; this.method = method; this.amount = amount; this.validationStatus = validationStatus;
        this.paymentStatusSnapshot = paymentStatusSnapshot; this.registeredBy = registeredBy; this.registeredAt = registeredAt;
    }

    public UUID attemptId() { return attemptId; }
    public PaymentMethod method() { return method; }
    public Cents amount() { return amount; }
    public ValidationStatus validationStatus() { return validationStatus; }
    public PaymentStatus paymentStatusSnapshot() { return paymentStatusSnapshot; }
    public UUID registeredBy() { return registeredBy; }
    public Instant registeredAt() { return registeredAt; }
}
