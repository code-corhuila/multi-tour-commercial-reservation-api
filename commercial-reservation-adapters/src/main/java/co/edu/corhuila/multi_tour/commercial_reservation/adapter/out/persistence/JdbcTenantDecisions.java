package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDecisionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-020 AC-20.5, AC-20.7 over financial_decision (freeze pair of HU-COM-016, resumed_due_at of this story): the open decisions of one tenant, read whole through the repository mapping, and
// the write of a frozen or resumed one through its save (the guard of the engine lets an open row change only its freeze and its shifted due instant). Every query leads with the tenant.
public final class JdbcTenantDecisions implements TenantDecisionStore {
    private final JdbcTemplate jdbc;
    private final JdbcFinancialDecisionRepository decisions;

    public JdbcTenantDecisions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.decisions = new JdbcFinancialDecisionRepository(jdbc);
    }

    @Override
    public List<FinancialDecision> openOf(UUID tenantId) {
        return jdbc.query("SELECT " + JdbcFinancialDecisionRepository.COLUMNS + " FROM commercial_reservation.financial_decision WHERE tenant_id = ? AND outcome IS NULL ORDER BY recognized_at, financial_decision_id",
                (row, index) -> JdbcFinancialDecisionRepository.map(row), tenantId);
    }

    @Override
    public void save(FinancialDecision decision) {
        decisions.save(decision);
    }
}
