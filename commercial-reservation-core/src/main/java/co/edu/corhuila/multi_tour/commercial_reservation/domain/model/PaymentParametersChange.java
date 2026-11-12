package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// HU-COM-009 DP-04, domain-events.md PaymentParametersChanged: the audit fact of one changed parameter row. It names the tenant, the author, the payment type, the service when the row
// has one, and the instant; it carries no value of a parameter, no reason and no personal data (the values stay in the row of payment_condition).
public record PaymentParametersChange(UUID tenantId, UUID actorId, PaymentType paymentType, UUID serviceId, Instant changedAt) {

    public PaymentParametersChange {
        if (tenantId == null) throw new DomainException("tenantId", "the tenant of the change is required");
        if (actorId == null) throw new DomainException("actorId", "the author of the change is required");
        if (paymentType == null) throw new DomainException("paymentType", "the payment type of the change is required");
        if (changedAt == null) throw new DomainException("changedAt", "the moment of the change is required");
    }
}
