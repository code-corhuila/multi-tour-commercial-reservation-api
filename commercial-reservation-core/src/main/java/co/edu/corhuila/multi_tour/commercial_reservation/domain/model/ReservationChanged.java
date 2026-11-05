package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// HU-COM-012 plan D-08, DP-D12-3: the modification shape of the event ReservationModified, told apart from the additional-discount shape by
// changeType. Values, party and a code only: no name, document, contact or free-text reason can travel (the db checks the nine keys).
public record ReservationChanged(UUID tenantId, UUID reservationId, ChangeType changeType, Cents previousFinalValue, Cents newFinalValue,
        Cents previousPendingBalance, Cents newPendingBalance, int partySize, boolean departureChanged, String reasonCode, UUID actorId,
        Instant occurredAt) implements PaymentEvent {
    public static final String STAFF_CHANGE = "STAFF_CHANGE";
}
