package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.time.Instant;
import java.util.UUID;

// HU-COM-020 RN-02, RN-03: one stored deadline row of a tenant with what the freeze and the resume need to judge it: the start of its departure and whether that departure was cancelled or interrupted.
public record TenantDeadline(UUID rowId, UUID tenantId, UUID reservationId, PaymentDeadline deadline, Instant departureStart, boolean departureCancelled) {

    public TenantDeadline withDeadline(PaymentDeadline next) {
        return new TenantDeadline(rowId, tenantId, reservationId, next, departureStart, departureCancelled);
    }
}
