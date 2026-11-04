package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.FollowupPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import java.util.UUID;

// Append and read only: no update and no delete exist on purpose (AC5, RN-05). The page starts at 1, newest first.
public interface PaymentFollowupRepository {
    void append(PaymentFollowup followup);

    FollowupPage page(UUID tenantId, UUID reservationId, int page, int limit);

    // One follow-up of the tenant by id, for the replay of a repeated request with the same Idempotency-Key (AC-11.9).
    default java.util.Optional<PaymentFollowup> find(UUID tenantId, UUID followupId) {
        throw new UnsupportedOperationException("the follow-up lookup needs the adapter override");
    }
}
