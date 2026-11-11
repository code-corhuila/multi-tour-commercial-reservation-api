package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import java.time.Instant;
import java.util.UUID;

// The notice of RF-019 as one more event of the outbox (HU-COM-015 plan D-03), written in the transaction of the fact that triggers it (norm 5.3.11).
// It carries references and a code, never an address, a name or an amount. Writing the same triggering event twice leaves one notice (RN-06).
public interface ClientNoticeOutbox {

    void append(UUID tenantId, UUID reservationId, NoticeType type, String triggeringEventType, UUID triggeringEventId, Instant occurredAt);
}
