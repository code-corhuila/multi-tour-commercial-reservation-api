package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashIncomeRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashSessionStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// The doubles of DP-02: the cash session, the income of the cash domain and its reversal, and the credit balance of
// HU-COM-017. They stay doubles until HU-CSH-001, HU-WFL-002 and HU-COM-017 exist (declared debt DD-2).
final class CashFakes implements CashSessionStatus, CashIncomeRecorder, CreditBalanceRecorder {

    boolean open = true;
    final List<String> income = new ArrayList<>();
    final List<UUID> reversed = new ArrayList<>();
    final List<Cents> excess = new ArrayList<>();
    private int sequence;

    @Override
    public boolean isOpen(UUID tenantId) {
        return open;
    }

    @Override
    public UUID record(UUID tenantId, UUID reservationId, Cents amount, UUID registeredBy) {
        income.add(reservationId + ":" + amount.amount() + ":" + registeredBy);
        return new UUID(9, 0xc00 + sequence++);
    }

    @Override
    public void reverse(UUID tenantId, UUID movementId) {
        reversed.add(movementId);
    }

    @Override
    public void recordExcess(UUID tenantId, UUID reservationId, UUID attemptId, Cents amount) {
        excess.add(amount);
    }
}
