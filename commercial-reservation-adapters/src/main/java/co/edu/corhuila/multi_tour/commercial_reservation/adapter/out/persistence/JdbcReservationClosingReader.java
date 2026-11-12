package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.instant;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationClosingReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Closing;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionSettlement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-019 DD-2 (rule PROPOSED, owner Project Lead): reads how far each financial decision of a reservation is settled (HU-COM-016 refunds, HU-COM-017 credit balances)
// and lets the domain rule Closing.of decide. A decision is settled when its refund is executed, its credit balance is recorded, or it was rejected. Every query leads
// with the tenant; nothing sensitive is read.
public final class JdbcReservationClosingReader implements ReservationClosingReader {
    private static final String SETTLEMENTS = "SELECT d.frozen_at IS NOT NULL AS frozen, CASE "
            + "WHEN d.outcome IN ('REFUND_FULL', 'AUTHORIZED') THEN (SELECT max(r.executed_at) FROM commercial_reservation.refund_authorization r "
            + "WHERE r.tenant_id = d.tenant_id AND r.financial_decision_id = d.financial_decision_id AND r.execution_state = 'EXECUTED') "
            + "WHEN d.outcome = 'CREDIT_FULL' THEN (SELECT max(c.recorded_at) FROM commercial_reservation.credit_balance c "
            + "WHERE c.tenant_id = d.tenant_id AND c.source_financial_decision_id = d.financial_decision_id) "
            + "WHEN d.outcome = 'REJECTED' THEN d.decided_at END AS settled_at "
            + "FROM commercial_reservation.financial_decision d WHERE d.tenant_id = ? AND d.reservation_id = ?";
    private final JdbcTemplate jdbc;

    public JdbcReservationClosingReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Closing> closingOf(UUID tenantId, UUID reservationId) {
        return Closing.of(jdbc.query(SETTLEMENTS, (row, index) -> new DecisionSettlement(instant(row, "settled_at"), row.getBoolean("frozen")), tenantId, reservationId));
    }
}
