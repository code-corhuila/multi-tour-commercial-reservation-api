package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDeadlineStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDecisionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEpisodeStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantInactivityEffects;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// HU-COM-020 RN-02, RN-03, RN-06, RN-14; AC-18.13, AC-20.2 to AC-20.5, AC-20.16, AC-20.18: the inactivation opens the suspension (once) and freezes every open deadline of the tenant, every open 24-hour
// financial decision and the clocks of every open rescheduling episode that has a proposal pending, at the instant of the event with their remaining time (the deadlines with the cause TENANT_INACTIVE); the
// reactivation closes the suspension (once) and resumes only the deadlines the inactivation froze, the frozen decisions and the frozen episodes. A reactivation with nothing open does nothing. It reads and writes the suspension, the deadline and the decision rows of one tenant, never a
// security timer.
public class TenantInactivityUseCase implements TenantInactivityEffects {

    private final SuspensionStore suspensions;
    private final TenantDeadlineStore deadlines;
    private final TenantDecisionStore decisions;
    private final TenantEpisodeStore episodes;
    private final IdGenerator ids;

    public TenantInactivityUseCase(SuspensionStore suspensions, TenantDeadlineStore deadlines, IdGenerator ids) {
        this(suspensions, deadlines, new TenantDecisionStore() {
            @Override public List<FinancialDecision> openOf(UUID tenantId) { return List.of(); }
            @Override public void save(FinancialDecision decision) { }
        }, ids);
    }

    public TenantInactivityUseCase(SuspensionStore suspensions, TenantDeadlineStore deadlines, TenantDecisionStore decisions, IdGenerator ids) {
        this(suspensions, deadlines, decisions, new TenantEpisodeStore() {
            @Override public List<ReschedulingEpisode> openOf(UUID tenantId) { return List.of(); }
            @Override public void save(ReschedulingEpisode episode) { }
        }, ids);
    }

    public TenantInactivityUseCase(SuspensionStore suspensions, TenantDeadlineStore deadlines, TenantDecisionStore decisions, TenantEpisodeStore episodes, IdGenerator ids) {
        this.suspensions = suspensions;
        this.deadlines = deadlines;
        this.decisions = decisions;
        this.episodes = episodes;
        this.ids = ids;
    }

    @Override
    public void inactivated(UUID tenantId, UUID eventId, Instant at) {
        if (!suspensions.open(ids.newId(), tenantId, at, eventId)) return;
        for (TenantDeadline row : deadlines.openOf(tenantId)) {
            var frozen = row.deadline().frozen(at, row.departureStart(), FreezeCause.TENANT_INACTIVE);
            if (!frozen.equals(row.deadline())) deadlines.save(row.withDeadline(frozen));
        }
        for (FinancialDecision decision : decisions.openOf(tenantId)) {
            if (!decision.isFrozen()) decisions.save(decision.freeze(at));
        }
        for (ReschedulingEpisode episode : episodes.openOf(tenantId)) {
            ReschedulingEpisode frozen = episode.freeze(at);
            if (!frozen.equals(episode)) episodes.save(frozen);
        }
    }

    @Override
    public void reactivated(UUID tenantId, UUID eventId, Instant at) {
        if (!suspensions.closeOpen(tenantId, at, eventId)) return;
        for (TenantDeadline row : deadlines.openOf(tenantId)) {
            var resumed = row.deadline().resumed(at, row.departureStart(), FreezeCause.TENANT_INACTIVE, row.departureCancelled() || reached(row, at));
            if (!resumed.equals(row.deadline())) deadlines.save(row.withDeadline(resumed));
        }
        for (FinancialDecision decision : decisions.openOf(tenantId)) {
            if (decision.isFrozen()) decisions.save(decision.resumed(at));
        }
        for (ReschedulingEpisode episode : episodes.openOf(tenantId)) {
            if (episode.isFrozen()) episodes.save(episode.resumed(at));
        }
    }

    // AC-20.9, PDR I1: the reactivation does not reactivate a departure whose time has already passed; it is handled as not attributable to the customer, so its deadline is not resumed to expire at the start.
    private static boolean reached(TenantDeadline row, Instant at) {
        return row.departureStart() != null && !row.departureStart().isAfter(at);
    }
}
