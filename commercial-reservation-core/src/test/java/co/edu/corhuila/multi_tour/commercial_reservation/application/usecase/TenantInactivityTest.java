package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDeadlineStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SuspensionIntervals;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEventType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-04 of HU-COM-020 (-api): RN-02, RN-03, RN-06, RN-14; AC-20.2 to AC-20.4, AC-20.6, AC-20.15, AC-20.16, AC-20.18, AC-20.19. The hook runs inside the transaction of the copy and the processed id and only
// for an event that moved the status: the inactivation opens the suspension and freezes every open deadline of that tenant at the instant of the event; the reactivation closes the suspension and resumes
// only what the inactivation froze. Another tenant is never touched; a repeat or a reactivation with nothing open does nothing. The JDBC adapters are a later pull request; the ports have doubles here.
class TenantInactivityTest {

    private static final UUID TENANT = new UUID(1, 1), OTHER = new UUID(1, 2);
    private static final Instant T0 = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant START = Instant.parse("2026-12-20T14:00:00Z");

    private final TenantEventFakes copies = new TenantEventFakes();
    private final Map<UUID, List<String>> suspensions = new LinkedHashMap<>();
    private final Map<UUID, TenantDeadline> deadlines = new LinkedHashMap<>();
    private final List<String> touched = new ArrayList<>();
    private final SuspensionStore suspensionStore = new SuspensionStore() {
        @Override public boolean open(UUID id, UUID tenantId, Instant at, UUID eventId) {
            List<String> rows = suspensions.computeIfAbsent(tenantId, t -> new ArrayList<>());
            if (!rows.isEmpty() && rows.get(rows.size() - 1).startsWith("open")) return false;
            rows.add("open@" + at + "/" + eventId);
            return true;
        }
        @Override public boolean closeOpen(UUID tenantId, Instant at, UUID eventId) {
            List<String> rows = suspensions.getOrDefault(tenantId, List.of());
            if (rows.isEmpty() || !rows.get(rows.size() - 1).startsWith("open")) return false;
            ((List<String>) suspensions.get(tenantId)).set(rows.size() - 1, "closed@" + at + "/" + eventId);
            return true;
        }
        @Override public SuspensionIntervals intervals(UUID tenantId) { return new SuspensionIntervals(List.of()); }
    };
    private final TenantDeadlineStore deadlineStore = new TenantDeadlineStore() {
        @Override public List<TenantDeadline> openOf(UUID tenantId) {
            touched.add("read:" + tenantId);
            return deadlines.values().stream().filter(d -> d.tenantId().equals(tenantId)).toList();
        }
        @Override public void save(TenantDeadline row) { touched.add("write:" + row.rowId()); deadlines.put(row.rowId(), row); }
    };
    private final TenantInactivityUseCase effects = new TenantInactivityUseCase(suspensionStore, deadlineStore, () -> UUID.randomUUID());
    private final ApplyTenantEventUseCase apply = new ApplyTenantEventUseCase(copies, copies, copies, effects);

    private static TenantEvent event(UUID tenant, TenantEventType type, Instant at) {
        return new TenantEvent(UUID.randomUUID(), type, tenant, at, type == TenantEventType.TENANT_CREATED ? "agencia-ficticia" : null,
                type == TenantEventType.TENANT_CREATED ? TenantStatus.ACTIVE : null, type == TenantEventType.TENANT_CREATED ? "America/Bogota" : null,
                type == TenantEventType.TENANT_CREATED ? "CO" : null);
    }

    private TenantDeadline row(UUID tenant, long id, PaymentDeadline deadline, boolean departureCancelled) {
        TenantDeadline row = new TenantDeadline(new UUID(5, id), tenant, new UUID(6, id), deadline, START, departureCancelled);
        deadlines.put(row.rowId(), row);
        return row;
    }

    private PaymentDeadline open(long hoursFromT0) {
        return new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, T0.plus(Duration.ofHours(hoursFromT0)));
    }

    private void start() {
        apply.apply(event(TENANT, TenantEventType.TENANT_CREATED, T0.minusSeconds(3600)));
        apply.apply(event(OTHER, TenantEventType.TENANT_CREATED, T0.minusSeconds(3600)));
    }

    @Test
    void freezeAndResumeTouchOnlyDeadlinesOfThatTenant() {
        start();
        row(TENANT, 1, open(5), false);
        row(OTHER, 2, open(5), false);

        apply.apply(event(TENANT, TenantEventType.TENANT_INACTIVATED, T0));

        assertThat(deadlines.get(new UUID(5, 1)).deadline().freezeCause()).isEqualTo(FreezeCause.TENANT_INACTIVE);
        assertThat(deadlines.get(new UUID(5, 1)).deadline().remainingAtFreezeMinutes()).isEqualTo(300);
        assertThat(touched).containsExactly("read:" + TENANT, "write:" + new UUID(5, 1));
    }

    @Test
    void anotherTenantIsNeverFrozenNorResumed() {
        start();
        row(OTHER, 2, open(5), false);

        apply.apply(event(TENANT, TenantEventType.TENANT_INACTIVATED, T0));
        apply.apply(event(TENANT, TenantEventType.TENANT_REACTIVATED, T0.plusSeconds(60)));

        assertThat(deadlines.get(new UUID(5, 2)).deadline().frozen()).isFalse();
        assertThat(touched).noneMatch(line -> line.contains(OTHER.toString()) || line.equals("write:" + new UUID(5, 2)));
        assertThat(suspensions).doesNotContainKey(OTHER);
    }

    @Test
    void aSuspensionIsOpenedOnceAndClosedOnce() {
        start();

        apply.apply(event(TENANT, TenantEventType.TENANT_INACTIVATED, T0));
        assertThat(suspensions.get(TENANT)).singleElement().asString().startsWith("open@" + T0);

        apply.apply(event(TENANT, TenantEventType.TENANT_REACTIVATED, T0.plus(Duration.ofHours(30))));
        assertThat(suspensions.get(TENANT)).singleElement().asString().startsWith("closed@" + T0.plus(Duration.ofHours(30)));
    }

    @Test
    void theSameEventTwiceFreezesOnceAndAResumeWithNothingOpenDoesNothing() {
        start();
        row(TENANT, 1, open(5), false);
        TenantEvent inactivated = event(TENANT, TenantEventType.TENANT_INACTIVATED, T0);

        apply.apply(inactivated);
        apply.apply(inactivated);
        assertThat(touched.stream().filter(line -> line.startsWith("write:")).count()).isEqualTo(1);

        TenantEvent noSuspension = event(OTHER, TenantEventType.TENANT_REACTIVATED, T0.plusSeconds(10));
        touched.clear();
        apply.apply(noSuspension);
        assertThat(touched).as("a reactivation of a tenant that was active does nothing").isEmpty();
    }

    @Test
    void resumingContinuesWithWhatRemainedAndLeavesOtherCausesAlone() {
        start();
        row(TENANT, 1, open(5), false);
        row(TENANT, 2, open(6).frozen(T0.minus(Duration.ofHours(1)), START, FreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED), false);
        apply.apply(event(TENANT, TenantEventType.TENANT_INACTIVATED, T0));
        Instant back = T0.plus(Duration.ofHours(30));

        apply.apply(event(TENANT, TenantEventType.TENANT_REACTIVATED, back));

        PaymentDeadline resumed = deadlines.get(new UUID(5, 1)).deadline();
        assertThat(resumed.frozen()).isFalse();
        assertThat(resumed.effective(START)).isEqualTo(back.plus(Duration.ofHours(5)));
        PaymentDeadline other = deadlines.get(new UUID(5, 2)).deadline();
        assertThat(other.frozen()).isTrue();
        assertThat(other.freezeCause()).isEqualTo(FreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED);
    }

    @Test
    void aDeadlineWhoseDepartureWasCancelledDuringTheInactivationIsNotResumed() {
        start();
        row(TENANT, 1, open(5), false);
        apply.apply(event(TENANT, TenantEventType.TENANT_INACTIVATED, T0));
        deadlines.put(new UUID(5, 1), new TenantDeadline(new UUID(5, 1), TENANT, new UUID(6, 1), deadlines.get(new UUID(5, 1)).deadline(), START, true));

        apply.apply(event(TENANT, TenantEventType.TENANT_REACTIVATED, T0.plus(Duration.ofHours(30))));

        PaymentDeadline kept = deadlines.get(new UUID(5, 1)).deadline();
        assertThat(kept.frozen()).isTrue();
        assertThat(kept.freezeCause()).isEqualTo(FreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED);
    }

    @Test
    void theHookTouchesOnlyTheSuspensionAndTheDeadlinesNeverASecurityTimer() {
        start();
        row(TENANT, 1, open(5), false);

        apply.apply(event(TENANT, TenantEventType.TENANT_INACTIVATED, T0));

        assertThat(TenantInactivityUseCase.class.getDeclaredFields()).extracting(field -> field.getType().getSimpleName())
                .containsExactlyInAnyOrder("SuspensionStore", "TenantDeadlineStore", "TenantDecisionStore", "TenantEpisodeStore", "IdGenerator");
    }

    @Test
    void aLocalizationChangeOrAnOlderEventDoesNotFreezeAnything() {
        start();
        row(TENANT, 1, open(5), false);

        apply.apply(event(TENANT, TenantEventType.TENANT_INACTIVATED, T0.minus(Duration.ofHours(3))));
        assertThat(deadlines.get(new UUID(5, 1)).deadline().frozen()).as("older than the copy: not applied").isFalse();
        TenantEvent localization = new TenantEvent(UUID.randomUUID(), TenantEventType.TENANT_LOCALIZATION_CHANGED, TENANT, T0, null, null, "America/Lima", "PE");
        touched.clear();
        apply.apply(localization);
        assertThat(touched).isEmpty();
    }
}
