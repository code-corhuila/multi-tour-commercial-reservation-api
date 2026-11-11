package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapters of the decision and the refund have their own persistence test. A test plants a decision or a refund and reads back what
// the routes wrote and evented.
@TestConfiguration
class RefundHttpFixture {
    @Bean @Primary MemoryDecisions memoryDecisions() { return new MemoryDecisions(); }

    @Bean @Primary MemoryRefunds memoryRefunds() { return new MemoryRefunds(); }

    @Bean @Primary MemorySnapshots memorySnapshots() { return new MemorySnapshots(); }

    static final class MemorySnapshots implements ReservationReader {
        final Map<UUID, ReservationSnapshot> snapshots = new LinkedHashMap<>();

        public Optional<ReservationSnapshot> findByTenantAndId(UUID tenantId, UUID reservationId) {
            return Optional.ofNullable(snapshots.get(reservationId)).filter(s -> s.tenantId().equals(tenantId));
        }
    }

    static final class MemoryDecisions implements FinancialDecisionRepository {
        final Map<UUID, FinancialDecision> decisions = new LinkedHashMap<>();

        void clear() { decisions.clear(); }

        public Optional<FinancialDecision> find(UUID tenantId, UUID id) {
            return Optional.ofNullable(decisions.get(id)).filter(d -> d.tenantId().equals(tenantId));
        }
        public Optional<FinancialDecision> findLatest(UUID tenantId, UUID reservationId, DecisionKind kind) {
            return decisions.values().stream().filter(d -> d.tenantId().equals(tenantId) && d.reservationId().equals(reservationId) && d.kind() == kind)
                    .reduce((first, second) -> second);
        }
        public void save(FinancialDecision decision) { decisions.put(decision.id(), decision); }
    }

    static final class MemoryRefunds implements RefundAuthorizationRepository, RefundAuthorizedOutbox {
        final Map<UUID, RefundAuthorization> refunds = new LinkedHashMap<>();
        final List<RefundAuthorization> events = new ArrayList<>();

        void clear() { refunds.clear(); events.clear(); }

        public Optional<RefundAuthorization> find(UUID tenantId, UUID id) {
            return Optional.ofNullable(refunds.get(id)).filter(r -> r.tenantId().equals(tenantId));
        }
        public Optional<RefundAuthorization> findByDecision(UUID tenantId, UUID decisionId) {
            return refunds.values().stream().filter(r -> r.tenantId().equals(tenantId) && decisionId.equals(r.financialDecisionId())).findFirst();
        }
        public void save(RefundAuthorization refund) { refunds.put(refund.id(), refund); }
        public void append(RefundAuthorization refund) { events.add(refund); }
    }
}
