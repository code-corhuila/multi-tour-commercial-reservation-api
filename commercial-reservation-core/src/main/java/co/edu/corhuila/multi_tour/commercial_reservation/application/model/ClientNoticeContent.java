package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import java.time.Instant;
import java.util.UUID;

// What job 4 needs to write one mail (AC-15.9): the recipient and the variables of the template, read at send time and never kept in an event.
public record ClientNoticeContent(UUID eventId, NoticeType notificationType, String recipientEmail, String recipientName, UUID reservationId,
        String tourName, Instant departureStartsAt, Long amountCents, Long pendingBalanceCents, String causeCode) {
}
