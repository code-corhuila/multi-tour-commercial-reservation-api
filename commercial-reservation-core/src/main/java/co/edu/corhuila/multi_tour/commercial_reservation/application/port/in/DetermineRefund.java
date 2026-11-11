package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.util.UUID;

// HU-COM-016 AC-16.7, DP-05, DP-15: the refund that the domain of operations determines for a cause not attributable to the customer or a personal emergency.
public interface DetermineRefund {

    // amountCents is null for a cause not attributable (the service computes it) and required for a personal emergency (the amount of the policy).
    RefundAuthorization determine(UUID tenantId, UUID reservationId, RefundOrigin origin, Long amountCents);
}
