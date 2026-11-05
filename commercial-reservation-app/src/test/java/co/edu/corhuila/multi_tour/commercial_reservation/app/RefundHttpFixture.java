package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
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

    @Bean @Primary MemoryQueries memoryQueries(MemoryDecisions decisions, MemoryRefunds refunds) { return new MemoryQueries(decisions, refunds, TestJwtKeys.NOW); }

    // The same filters as the SQL of the JDBC queries, over the planted rows: pending is an open decision or a REFUND_FULL without refund; attention is pending
    // and not frozen (DP-09); overdue is past the due instant and not executed.
    static final class MemoryQueries implements RefundQueries {
        private final MemoryDecisions decisions;
        private final MemoryRefunds refunds;
        private final java.time.Instant now;

        MemoryQueries(MemoryDecisions decisions, MemoryRefunds refunds, java.time.Instant now) { this.decisions = decisions; this.refunds = refunds; this.now = now; }

        private boolean pending(FinancialDecision d) {
            return d.outcome() == null || (d.outcome() == DecisionOutcome.REFUND_FULL && refunds.findByDecision(d.tenantId(), d.id()).isEmpty());
        }
        private static <T> RefundPage<T> page(List<T> all, int page, int limit) {
            return new RefundPage<>(all.stream().skip((long) (page - 1) * limit).limit(limit).toList(), all.size());
        }
        public RefundPage<FinancialDecision> decisions(UUID tenantId, DecisionKind kind, boolean pending, boolean attention, int page, int limit) {
            return page(decisions.decisions.values().stream().filter(d -> d.tenantId().equals(tenantId) && (kind == null || d.kind() == kind)
                    && (!pending || pending(d)) && (!attention || (pending(d) && !d.isFrozen()))).toList(), page, limit);
        }
        public RefundPage<FinancialDecision> dueDecisions(UUID tenantId, java.time.Instant at, int page, int limit) {
            return page(decisions.decisions.values().stream().filter(d -> d.tenantId().equals(tenantId) && d.isDue(at)).toList(), page, limit);
        }
        public RefundPage<RefundAuthorization> refunds(UUID tenantId, ExecutionState state, boolean overdue, java.time.Instant at, UUID decisionId, int page, int limit) {
            return page(refunds.refunds.values().stream().filter(r -> r.tenantId().equals(tenantId) && (state == null || r.state() == state)
                    && (!overdue || r.isOverdue(at)) && (decisionId == null || decisionId.equals(r.financialDecisionId()))).toList(), page, limit);
        }
        public RefundPage<RefundAuthorization> dueRefunds(UUID tenantId, java.time.Instant at, int page, int limit) {
            return page(refunds.refunds.values().stream().filter(r -> r.tenantId().equals(tenantId) && r.overdueMarkedAt() == null && r.isOverdue(at)).toList(), page, limit);
        }
    }

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
