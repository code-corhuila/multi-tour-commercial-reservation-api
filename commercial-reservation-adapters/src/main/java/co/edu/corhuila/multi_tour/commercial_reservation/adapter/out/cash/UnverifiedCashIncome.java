package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashIncomeRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;

// The income and its reversal are written in the cash only through the saga register-payment (ADR-004 D4, HU-WFL-002). Until the
// saga starts the step, they are refused with an explicit message and nothing is stored (declared debt DD-2). It is a separate
// object from UnverifiedCashSession so that it is not also a CashSessionStatus (two beans of that type are ambiguous).
public final class UnverifiedCashIncome implements CashIncomeRecorder {

    private final UnverifiedCashSession refusal = new UnverifiedCashSession();

    @Override
    public UUID record(UUID tenantId, UUID reservationId, Cents amount, UUID registeredBy) {
        return refusal.record(tenantId, reservationId, amount, registeredBy);
    }

    @Override
    public void reverse(UUID tenantId, UUID movementId) {
        refusal.reverse(tenantId, movementId);
    }
}
