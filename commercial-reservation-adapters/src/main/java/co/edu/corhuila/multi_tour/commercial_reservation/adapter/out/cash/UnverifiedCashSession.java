package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashIncomeRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashSessionStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;

// DOCUMENTED STAND-IN (declared debt DD-2, DP-02): the cash session and the cash income belong to Cash (HU-CSH-001) and the saga
// register-payment (HU-WFL-002), neither of which exists yet. Until the real adapter replaces this one, a cash session can never be
// verified, so no cash payment is accepted: the answer is the rule "there is no open cash session" and nothing is stored. It never
// pretends that money reached the cash box. A transfer is not affected.
public final class UnverifiedCashSession implements CashSessionStatus, CashIncomeRecorder {

    @Override
    public boolean isOpen(UUID tenantId) {
        return false;
    }

    @Override
    public UUID record(UUID tenantId, UUID reservationId, Cents amount, UUID registeredBy) {
        throw new BusinessRuleViolation("the cash income cannot be recorded until the cash domain is available");
    }

    @Override
    public void reverse(UUID tenantId, UUID movementId) {
        throw new BusinessRuleViolation("the cash income cannot be reversed until the cash domain is available");
    }
}
