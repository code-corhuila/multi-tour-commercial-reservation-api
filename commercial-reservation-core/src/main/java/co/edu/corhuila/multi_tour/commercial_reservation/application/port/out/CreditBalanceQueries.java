package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditSummary;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// HU-COM-017 AC-17.9, AC-17.13, AC-17.16: the reads of the credit balances. Every query leads with the tenant, and the customer is a condition of the query when the
// reader is a Final Customer, never a filter applied afterwards. The available value is derived, never stored.
public interface CreditBalanceQueries {

    // customerId null reads every customer of the tenant; availableOnly keeps the credits with value left.
    RefundPage<CreditBalanceView> page(UUID tenantId, UUID customerId, boolean availableOnly, int page, int limit);

    Optional<CreditBalanceView> find(UUID tenantId, UUID customerId, UUID creditBalanceId);

    List<CreditApplication> applications(UUID tenantId, UUID creditBalanceId);

    List<FinancialDecision> refundRequests(UUID tenantId, UUID creditBalanceId);

    CreditSummary summary(UUID tenantId);
}
