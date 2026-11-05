package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ModificationOutcome;
import java.time.Instant;
import java.util.UUID;

// AC-12.6, RN-08: one immutable row of the history of a reservation. The reason is free text typed by staff: it is stored and read, never
// copied to an event or a log. The tour is the reserved one (the record keeps it once because it cannot change in this version).
public record ReservationModificationRecord(UUID modificationId, UUID reservationId, ChangeType changeType, String reason,
        ModificationOutcome outcome, int previousPartySize, int newPartySize, UUID serviceId, UUID previousSalidaId, UUID newSalidaId,
        UUID registeredBy, Instant registeredAt) {
}
