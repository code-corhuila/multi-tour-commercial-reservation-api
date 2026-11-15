package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Instant;
import java.util.UUID;

// HU-COM-016 AC-16.7, spec 026: the refund that the service itself computes for a cause not attributable to the customer (100 percent of the validated
// value) or for a personal emergency (the share of the affected travelers minus the policy). No caller gives an amount. A repeat for the same reservation and
// origin returns the stored refund. When nothing is due, NoRefundDueException says so and nothing is written.
public interface DetermineRefund {

    // affectedTravelers is null for a cause not attributable and required for a personal emergency. occurredAt is the instant of the cause (null: now).
    RefundAuthorization determine(UUID tenantId, UUID reservationId, RefundOrigin origin, Integer affectedTravelers, Instant occurredAt);

    default RefundAuthorization determine(UUID tenantId, UUID reservationId, RefundOrigin origin, Integer affectedTravelers) {
        return determine(tenantId, reservationId, origin, affectedTravelers, null);
    }
}
