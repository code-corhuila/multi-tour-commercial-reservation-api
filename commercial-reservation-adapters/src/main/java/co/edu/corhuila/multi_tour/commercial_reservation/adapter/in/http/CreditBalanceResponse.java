package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceView;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import java.util.UUID;

// CreditBalance of the contract (AC-17.9): the available value is derived by the aggregate, never stored. Money is cents. The reason of a request never travels here.
public record CreditBalanceResponse(UUID creditBalanceId, UUID sourceReservationId, UUID customerId, String origin, long recordedCents, long appliedCents,
        long inRefundCents, long availableCents, String recordedAt, UUID recordedBy) {

    static CreditBalanceResponse of(CreditBalanceView view) {
        CreditBalance c = view.credit();
        return new CreditBalanceResponse(c.id(), c.sourceReservationId(), view.customerId(), c.origin().name(), c.amount().amount(), c.applied().amount(),
                c.setAside().amount(), c.available().amount(), c.recordedAt().toString(), c.recordedBy());
    }
}
