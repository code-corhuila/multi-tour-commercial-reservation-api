package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.util.Optional;
import java.util.UUID;

// HU-COM-016 plan D-02: scoped to the tenant like the decisions.
public interface RefundAuthorizationRepository {

    Optional<RefundAuthorization> find(UUID tenantId, UUID refundAuthorizationId);

    // The first refund authorized for a decision; a second authorization of the same decision is refused (AC-16.19).
    Optional<RefundAuthorization> findByDecision(UUID tenantId, UUID financialDecisionId);

    // Spec 026, AC-26.8: the first refund of a reservation and an origin, so a cause is determined once. Empty for another tenant.
    Optional<RefundAuthorization> findByReservationAndOrigin(UUID tenantId, UUID reservationId, RefundOrigin origin);

    // Inserts a new refund or updates the one with that identifier.
    void save(RefundAuthorization refund);
}
