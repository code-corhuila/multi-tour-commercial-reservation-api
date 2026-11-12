package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDeadlineStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDecisionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEpisodeStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SuspensionIntervals;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-020 (-api), AC-18.13, AC-20.2, AC-20.3, AC-20.6, AC-20.15: the inactivation also freezes the clocks of every open rescheduling episode of the tenant that has a proposal pending, at the instant
// of the event; the reactivation resumes them with what remained. An episode with no proposal pending keeps running, a closed one and one of another tenant are never touched.
class TenantInactivityEpisodesTest {

    private static final UUID TENANT = new UUID(4, 1), OTHER = new UUID(4, 2);
    private static final Instant CANCELLED_AT = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant NOTIFIED = CANCELLED_AT.plus(Duration.ofHours(2));
    private static final Instant START = Instant.parse("2026-12-20T14:00:00Z");
    private static final Instant T0 = NOTIFIED.plus(Duration.ofHours(10));

    private final Map<UUID, ReschedulingEpisode> episodes = new LinkedHashMap<>();
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
    private final TenantDecisionStore decisions = new TenantDecisionStore() {
        @Override public List<FinancialDecision> openOf(UUID tenantId) { return List.of(); }
        @Override public void save(FinancialDecision decision) { }
    };
    private final TenantEpisodeStore store = new TenantEpisodeStore() {
        @Override public List<ReschedulingEpisode> openOf(UUID tenantId) {
            return episodes.values().stream().filter(e -> e.tenantId().equals(tenantId) && e.state() == EpisodeState.OPEN).toList();
        }
        @Override public void save(ReschedulingEpisode episode) { writes.add("write:" + episode.id()); episodes.put(episode.id(), episode); }
    };
    private final TenantInactivityUseCase effects = new TenantInactivityUseCase(suspensions, deadlines, decisions, store, () -> UUID.randomUUID());

    private ReschedulingEpisode episode(UUID tenant, long id, boolean proposed) {
        ReschedulingEpisode open = ReschedulingEpisode.open(new UUID(5, id), tenant, UUID.randomUUID(), UUID.randomUUID(), CANCELLED_AT, CANCELLED_AT);
        ReschedulingEpisode e = proposed ? open.propose(UUID.randomUUID(), UUID.randomUUID(), 2, UUID.randomUUID(), NOTIFIED, START) : open;
        episodes.put(e.id(), e);
        return e;
    }

    @Test
    void theInactivationFreezesTheEpisodesOfTheTenantThatWaitForAnAnswerAndNothingElse() {
        ReschedulingEpisode waiting = episode(TENANT, 1, true);
        ReschedulingEpisode running = episode(TENANT, 2, false);
        ReschedulingEpisode other = episode(OTHER, 3, true);
        ReschedulingEpisode closed = episode(TENANT, 4, false);
        episodes.put(closed.id(), closed.declareNoAlternative(NOTIFIED));

        effects.inactivated(TENANT, UUID.randomUUID(), T0);

        assertThat(episodes.get(waiting.id()).frozenAt()).isEqualTo(T0);
        assertThat(episodes.get(running.id()).isFrozen()).as("no proposal pending: the clocks keep running").isFalse();
        assertThat(episodes.get(other.id()).isFrozen()).as("another tenant is never touched").isFalse();
        assertThat(writes).containsExactly("write:" + waiting.id());
    }

    @Test
    void theReactivationResumesThemWithWhatRemainedAndTheSameEventTwiceDoesNothingMore() {
        ReschedulingEpisode waiting = episode(TENANT, 1, true);
        episode(OTHER, 3, true);
        effects.inactivated(TENANT, UUID.randomUUID(), T0);
        writes.clear();
        Instant back = T0.plus(Duration.ofHours(30));

        effects.reactivated(TENANT, UUID.randomUUID(), back);

        ReschedulingEpisode resumed = episodes.get(waiting.id());
        assertThat(resumed.isFrozen()).isFalse();
        assertThat(resumed.frozen()).isEqualTo(Duration.ofHours(30));
        assertThat(resumed.episodeLimit()).isEqualTo(waiting.episodeLimit().plus(Duration.ofHours(30)));
        assertThat(writes).containsExactly("write:" + waiting.id());
        writes.clear();
        effects.reactivated(TENANT, UUID.randomUUID(), back.plusSeconds(5));
        assertThat(writes).isEmpty();
    }
}
