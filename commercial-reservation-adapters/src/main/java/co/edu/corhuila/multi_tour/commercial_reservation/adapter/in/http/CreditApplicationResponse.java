package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import java.util.UUID;

// CreditBalanceApplication of the contract (AC-17.8): who applied it and whether as the customer or as staff.
public record CreditApplicationResponse(UUID creditBalanceApplicationId, UUID creditBalanceId, UUID targetReservationId, long amountCents, UUID appliedBy,
        String appliedAs, String appliedAt) {

    static CreditApplicationResponse of(CreditApplication a) {
        return new CreditApplicationResponse(a.id(), a.creditBalanceId(), a.targetReservationId(), a.amount().amount(), a.appliedBy(), a.kind().name(),
                a.appliedAt().toString());
    }
}
