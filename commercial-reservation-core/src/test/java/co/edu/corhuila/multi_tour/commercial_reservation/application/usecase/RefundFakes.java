package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.BusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// In-memory doubles for the refund use cases of HU-COM-016 (-api). The calendar adds plain days: the weekday rule has its own test.
final class RefundFakes {

    static final UUID TENANT = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    final Map<UUID, FinancialDecision> decisions = new LinkedHashMap<>();
    final Map<UUID, RefundAuthorization> refunds = new LinkedHashMap<>();
    final List<RefundAuthorization> events = new ArrayList<>();
    int transactions;
    private int sequence;

    final FinancialDecisionRepository decisionRepository = new FinancialDecisionRepository() {
        @Override public Optional<FinancialDecision> find(UUID tenantId, UUID id) {
            return Optional.ofNullable(decisions.get(id)).filter(d -> d.tenantId().equals(tenantId));
        }
        @Override public Optional<FinancialDecision> findLatest(UUID tenantId, UUID reservationId, DecisionKind kind) {
            return decisions.values().stream().filter(d -> d.tenantId().equals(tenantId) && d.reservationId().equals(reservationId) && d.kind() == kind)
                    .reduce((first, second) -> second);
        }
        @Override public void save(FinancialDecision decision) { decisions.put(decision.id(), decision); }
    };

    final RefundAuthorizationRepository refundRepository = new RefundAuthorizationRepository() {
        @Override public Optional<RefundAuthorization> find(UUID tenantId, UUID id) {
            return Optional.ofNullable(refunds.get(id)).filter(r -> r.tenantId().equals(tenantId));
        }
        @Override public Optional<RefundAuthorization> findByDecision(UUID tenantId, UUID decisionId) {
            return refunds.values().stream().filter(r -> r.tenantId().equals(tenantId) && decisionId.equals(r.financialDecisionId())).findFirst();
        }
        @Override public void save(RefundAuthorization refund) { refunds.put(refund.id(), refund); }
    };

    final BusinessCalendar calendar = (from, days) -> from.plus(Duration.ofDays(days));
    final RefundAuthorizedOutbox outbox = events::add;
    final IdGenerator ids = () -> new UUID(0, ++sequence);
    final PaymentTransaction transaction = new PaymentTransaction() {
        @Override public <T> T inTransaction(Supplier<T> work) { transactions++; return work.get(); }
    };

    static PaymentActor actor(Role role) {
        return new PaymentActor(TENANT, UUID.randomUUID(), role);
    }

    FinancialDecision open(DecisionKind kind, long base) {
        FinancialDecision decision = FinancialDecision.open(ids.newId(), TENANT, UUID.randomUUID(), kind, new co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents(base),
                null, kind == DecisionKind.CREDIT_BALANCE_REFUND_REQUEST ? UUID.randomUUID() : null, NOW);
        decisions.put(decision.id(), decision);
        return decision;
    }
}
