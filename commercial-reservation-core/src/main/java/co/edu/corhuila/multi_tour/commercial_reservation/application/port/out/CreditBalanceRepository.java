package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import java.util.Optional;
import java.util.UUID;

// HU-COM-017 plan D-02, D-03: every read is scoped to the tenant, so a credit balance of another tenant looks missing (AC-17.14). Each credit has one source, a
// decision or a payment attempt, and the lookups by source let a repeat recording find the credit it already made.
public interface CreditBalanceRepository {

    Optional<CreditBalance> find(UUID tenantId, UUID creditBalanceId);

    Optional<CreditBalance> findByDecision(UUID tenantId, UUID financialDecisionId);

    Optional<CreditBalance> findByAttempt(UUID tenantId, UUID paymentAttemptId);

    // Inserts a new credit balance or updates the one with that identifier.
    void save(CreditBalance creditBalance);
}
