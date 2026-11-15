package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.util.Optional;
import java.util.UUID;

// HU-COM-016 plan D-02: every read is scoped to the tenant, so a decision of another tenant looks missing (AC-16.13).
public interface FinancialDecisionRepository {

    Optional<FinancialDecision> find(UUID tenantId, UUID financialDecisionId);

    // The latest decision of a kind for a reservation: the open one takes a late value, a decided one makes the value follow its refund.
    Optional<FinancialDecision> findLatest(UUID tenantId, UUID reservationId, DecisionKind kind);

    // Inserts a new decision or updates the one with that identifier.
    void save(FinancialDecision decision);
}
