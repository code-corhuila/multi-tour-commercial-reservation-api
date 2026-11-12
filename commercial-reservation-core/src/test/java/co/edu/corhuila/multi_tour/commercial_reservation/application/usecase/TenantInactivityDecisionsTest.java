package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDeadlineStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDecisionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SuspensionIntervals;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-04 of HU-COM-020 (-api): RN-02, RN-03, RN-04; AC-20.5, AC-20.7. The inactivation also freezes every open 24-hour financial decision of the tenant at the instant of the event, with the time it had
// left; the reactivation resumes them with what remained (the shifted due instant). A decision of another tenant and a decided one are never touched.
class TenantInactivityDecisionsTest {

    private static final UUID TENANT = new UUID(2, 1), OTHER = new UUID(2, 2);
    private static final Instant T0 = Instant.parse("2026-11-10T08:00:00Z");

    private final Map<UUID, FinancialDecision> decisions = new LinkedHashMap<>();
    private final List<String> writes = new ArrayList<>();
    private final SuspensionStore suspensions = new SuspensionStore() {
        @Override public boolean open(UUID id, UUID tenantId, Instant at, UUID eventId) { return true; }
        @Override public boolean closeOpen(UUID tenantId, Instant at, UUID eventId) { return true; }
        @Override public SuspensionIntervals intervals(UUID tenantId) { return new SuspensionIntervals(List.of()); }
    };
    private final TenantDeadlineStore deadlines = new TenantDeadlineStore() {
        @Override public List<TenantDeadline> openOf(UUID tenantId) { return List.of(); }
        @Override public void save(TenantDeadline row) { }
    };
    private final TenantDecisionStore store = new TenantDecisionStore() {
        @Override public List<FinancialDecision> openOf(UUID tenantId) { return decisions.values().stream().filter(d -> d.tenantId().equals(tenantId) && !d.isDecided()).toList(); }
        @Override public void save(FinancialDecision decision) { writes.add("write:" + decision.id()); decisions.put(decision.id(), decision); }
    };
    private final TenantInactivityUseCase effects = new TenantInactivityUseCase(suspensions, deadlines, store, () -> UUID.randomUUID());

    private FinancialDecision decision(UUID tenant, long id, Instant recognized) {
        FinancialDecision d = FinancialDecision.open(new UUID(3, id), tenant, UUID.randomUUID(), DecisionKind.CUSTOMER_CANCELLATION, new Cents(100_000), new Cents(0), null, recognized);
        decisions.put(d.id(), d);
        return d;
    }

    @Test
    void theInactivationFreezesTheOpenDecisionsOfTheTenantWithTheTimeTheyHadLeft() {
        FinancialDecision mine = decision(TENANT, 1, T0.minus(Duration.ofHours(4)));
        FinancialDecision other = decision(OTHER, 2, T0.minus(Duration.ofHours(4)));
        FinancialDecision decided = decision(TENANT, 3, T0.minus(Duration.ofHours(5)));
        decisions.put(decided.id(), decided.recordRefundFull(UUID.randomUUID(), T0.minusSeconds(60)));

        effects.inactivated(TENANT, UUID.randomUUID(), T0);

        assertThat(decisions.get(mine.id()).isFrozen()).isTrue();
        assertThat(decisions.get(mine.id()).remainingAtFreezeMinutes()).isEqualTo(20 * 60);
        assertThat(decisions.get(other.id()).isFrozen()).isFalse();
        assertThat(writes).containsExactly("write:" + mine.id());
    }

    @Test
    void theReactivationResumesThemWithWhatRemainedAndLeavesTheOthersAlone() {
        FinancialDecision mine = decision(TENANT, 1, T0.minus(Duration.ofHours(4)));
        FinancialDecision other = decision(OTHER, 2, T0.minus(Duration.ofHours(4)));
        effects.inactivated(TENANT, UUID.randomUUID(), T0);
        writes.clear();
        Instant back = T0.plus(Duration.ofHours(30));

        effects.reactivated(TENANT, UUID.randomUUID(), back);

        FinancialDecision resumed = decisions.get(mine.id());
        assertThat(resumed.isFrozen()).isFalse();
        assertThat(resumed.dueAt()).isEqualTo(back.plus(Duration.ofHours(20)));
        assertThat(decisions.get(other.id()).dueAt()).isEqualTo(T0.minus(Duration.ofHours(4)).plus(Duration.ofHours(24)));
        assertThat(writes).containsExactly("write:" + mine.id());
    }
}
