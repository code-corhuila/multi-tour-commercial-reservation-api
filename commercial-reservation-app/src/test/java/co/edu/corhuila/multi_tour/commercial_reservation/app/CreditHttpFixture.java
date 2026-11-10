package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditSummary;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditApplicationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceApplications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapters of the credit balance ports are exercised by CreditBalancePersistenceTest and the credit JDBC HTTP tests. The queries apply the same
// conditions as the SQL (the tenant always, the customer when the reader is a Final Customer), so the routes can be tested without a database.
@TestConfiguration
class CreditHttpFixture {
    @Bean @Primary MemoryCredits memoryCredits() { return new MemoryCredits(); }

    static final class MemoryCredits implements CreditBalanceRepository, CreditApplicationReader, CreditBalanceApplications, CreditBalanceQueries {
        final Map<UUID, CreditBalance> credits = new LinkedHashMap<>();
        final Map<UUID, UUID> holders = new LinkedHashMap<>();
        final List<CreditApplication> applications = new ArrayList<>();

        void clear() { credits.clear(); holders.clear(); applications.clear(); }

        void plant(CreditBalance credit, UUID customerId) { credits.put(credit.id(), credit); holders.put(credit.id(), customerId); }

        @Override public Optional<CreditBalance> find(UUID tenantId, UUID creditBalanceId) {
            return Optional.ofNullable(credits.get(creditBalanceId)).filter(found -> found.tenantId().equals(tenantId));
        }
        @Override public Optional<CreditBalance> findByDecision(UUID tenantId, UUID financialDecisionId) { return Optional.empty(); }
        @Override public Optional<CreditBalance> findByAttempt(UUID tenantId, UUID paymentAttemptId) { return Optional.empty(); }
        @Override public void save(CreditBalance creditBalance) { credits.put(creditBalance.id(), creditBalance); }
        @Override public Optional<CreditApplication> findApplication(UUID tenantId, UUID applicationId) {
            return applications.stream().filter(found -> found.tenantId().equals(tenantId) && found.id().equals(applicationId)).findFirst();
        }
        @Override public void save(CreditApplication application) { applications.add(application); }

        @Override public RefundPage<CreditBalanceView> page(UUID tenantId, UUID customerId, boolean availableOnly, int page, int limit) {
            var found = credits.values().stream().filter(c -> c.tenantId().equals(tenantId) && (customerId == null || customerId.equals(holders.get(c.id())))
                    && (!availableOnly || c.available().amount() > 0)).map(c -> new CreditBalanceView(c, holders.get(c.id()))).toList();
            return new RefundPage<>(found.stream().skip((long) (page - 1) * limit).limit(limit).toList(), found.size());
        }
        @Override public Optional<CreditBalanceView> find(UUID tenantId, UUID customerId, UUID creditBalanceId) {
            return find(tenantId, creditBalanceId).filter(c -> customerId == null || customerId.equals(holders.get(c.id()))).map(c -> new CreditBalanceView(c, holders.get(c.id())));
        }
        @Override public List<CreditApplication> applications(UUID tenantId, UUID creditBalanceId) {
            return applications.stream().filter(a -> a.tenantId().equals(tenantId) && a.creditBalanceId().equals(creditBalanceId)).toList();
        }
        @Override public List<FinancialDecision> refundRequests(UUID tenantId, UUID creditBalanceId) { return List.of(); }
        @Override public CreditSummary summary(UUID tenantId) {
            var own = credits.values().stream().filter(c -> c.tenantId().equals(tenantId)).toList();
            return new CreditSummary(own.stream().mapToLong(c -> c.available().amount()).sum(), own.stream().map(c -> holders.get(c.id())).distinct().count(), 0);
        }
    }
}
