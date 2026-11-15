package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import java.util.UUID;

// PaymentFollowup of the contract. The note is free text: it is answered to the staff and never logged.
public record FollowupResponse(UUID followupId, UUID reservationId, String note, UUID contactedBy, String contactedAt) {

    static FollowupResponse of(PaymentFollowup followup) {
        return new FollowupResponse(followup.followupId(), followup.reservationId(), followup.note(), followup.contactedBy(),
                followup.contactedAt().toString());
    }
}
