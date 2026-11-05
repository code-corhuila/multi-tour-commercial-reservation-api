package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import java.time.Instant;
import java.util.UUID;

// One notice of the outbox as the reader resolves it: references and the variables of its fact, never an address or a name. The recipient is read apart,
// from the customer record, at send time (HU-COM-015 plan D-05).
public record StoredNotice(UUID eventId, UUID tenantId, UUID reservationId, UUID customerId, NoticeType type, String tourName, Instant departureStartsAt,
        Long amountCents, Long pendingBalanceCents, String causeCode) {
}
