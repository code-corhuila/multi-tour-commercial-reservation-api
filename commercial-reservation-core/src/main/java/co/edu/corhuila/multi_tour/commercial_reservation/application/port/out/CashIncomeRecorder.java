package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;

// Step 1 of a cash payment (ADR-004 D3): Cash records the income first, forwarding the user who registered the cash, and reverses
// it if a later step fails. Decided 2026-11-12 (PROPOSED, HU-COM-009): the commercial api records it directly in the cash.
// intentKey is stable for one intention (the same request repeated gives the same key), so a retry never records a second income.
// A refusal of the cash is a BusinessRuleViolation; a cash that does not answer is a CashUnavailableException.
public interface CashIncomeRecorder {

    UUID record(UUID tenantId, UUID reservationId, String intentKey, Cents amount, UUID registeredBy);

    void reverse(UUID tenantId, UUID movementId);
}
