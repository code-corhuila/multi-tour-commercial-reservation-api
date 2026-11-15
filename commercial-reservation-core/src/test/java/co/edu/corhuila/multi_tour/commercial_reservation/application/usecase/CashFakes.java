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
    RuntimeException recordFailure;
    RuntimeException reverseFailure;
    final List<String> income = new ArrayList<>();
    final List<String> keys = new ArrayList<>();
    private final java.util.Map<String, UUID> byKey = new java.util.HashMap<>();
    final List<UUID> reversed = new ArrayList<>();
    final List<Cents> excess = new ArrayList<>();
    private int sequence;

    @Override
    public boolean isOpen(UUID tenantId) {
        return open;
    }

    @Override
    public UUID record(UUID tenantId, UUID reservationId, String intentKey, Cents amount, UUID registeredBy) {
        if (recordFailure != null) throw recordFailure;
        keys.add(intentKey);
        // The cash replays the movement of a key it already has, like the real route (200 after 201).
        UUID known = byKey.get(intentKey);
        if (known != null) return known;
        income.add(reservationId + ":" + amount.amount() + ":" + registeredBy);
        UUID movement = new UUID(9, 0xc00 + sequence++);
        byKey.put(intentKey, movement);
        return movement;
    }

    @Override
    public void reverse(UUID tenantId, UUID movementId) {
        if (reverseFailure != null) throw reverseFailure;
        reversed.add(movementId);
    }

    @Override
    public void recordExcess(UUID tenantId, UUID reservationId, UUID attemptId, Cents amount) {
        excess.add(amount);
    }
}
