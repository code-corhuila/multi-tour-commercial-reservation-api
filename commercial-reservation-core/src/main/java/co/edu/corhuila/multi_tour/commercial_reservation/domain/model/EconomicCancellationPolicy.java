package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;

// HU-COM-020 RN-08; AC-20.9, AC-20.11, plan D-07: when the tenant is suspended and the departure was cancelled, or its scheduled time arrived, the reservation is never cancelled for economic
// non-compliance and its cause is NOT_ATTRIBUTABLE_TO_CUSTOMER (rescheduling or a 100 % refund, no penalty). Pure; the instant is an argument.
public final class EconomicCancellationPolicy {

    private EconomicCancellationPolicy() {
    }

    public static boolean allows(DepartureState departure, boolean tenantSuspended, Instant now) {
        if (!tenantSuspended) return true;
        boolean reached = departure.startsAt() != null && !now.isBefore(departure.startsAt());
        return departure.status() != DepartureStatus.CANCELLED && !reached;
    }

    public static CancellationCause causeFor(DepartureState departure, boolean tenantSuspended, Instant now) {
        return allows(departure, tenantSuspended, now) ? CancellationCause.PAYMENT_EXPIRY : CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER;
    }
}
