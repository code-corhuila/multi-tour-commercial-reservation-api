package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.util.List;
import java.util.UUID;

// HU-COM-020 AC-20.5, AC-20.7: the open 24-hour financial decisions of one tenant, and the write of a frozen or resumed one. It reads and writes the rows of that tenant only; called inside the
// transaction of the tenant event.
public interface TenantDecisionStore {

    List<FinancialDecision> openOf(UUID tenantId);

    void save(FinancialDecision decision);
}
