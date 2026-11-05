package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ClientNoticeContent;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

// ClientNoticeContent of the contract: only the fields the template needs are present, the others are absent.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientNoticeContentResponse(UUID eventId, String notificationType, String recipientEmail, String recipientName, UUID reservationId,
        String tourName, Instant departureStartsAt, Long amountCents, Long pendingBalanceCents, String causeCode) {

    static ClientNoticeContentResponse of(ClientNoticeContent content) {
        return new ClientNoticeContentResponse(content.eventId(), content.notificationType().name(), content.recipientEmail(), content.recipientName(),
                content.reservationId(), content.tourName(), content.departureStartsAt(), content.amountCents(), content.pendingBalanceCents(),
                content.causeCode());
    }
}
