package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// HU-COM-018 plan D-02, RN-04, RN-07: the incident of one reservation whose departure was cancelled for a cause not attributable to the customer. It is a relationship,
// never a state of the reservation. It holds the clocks (counted from the cancellation instant) and its proposals; substitute proposals never restart the limit.
// Immutable: every move returns the next episode.
public record ReschedulingEpisode(UUID id, UUID tenantId, UUID reservationId, UUID originalSalidaId, Instant cancelledAt, Instant openedAt, EpisodeState state,
        ClosingReason reason, Instant closedAt, Instant periodStart, Duration frozen, List<ReschedulingProposal> proposals, Instant frozenAt, Long remainingEpisodeMinutes) {

    public ReschedulingEpisode(UUID id, UUID tenantId, UUID reservationId, UUID originalSalidaId, Instant cancelledAt, Instant openedAt, EpisodeState state, ClosingReason reason,
            Instant closedAt, Instant periodStart, Duration frozen, List<ReschedulingProposal> proposals) {
        this(id, tenantId, reservationId, originalSalidaId, cancelledAt, openedAt, state, reason, closedAt, periodStart, frozen, proposals, null, null);
    }

    public ReschedulingEpisode {
        if (id == null || tenantId == null || reservationId == null || originalSalidaId == null || cancelledAt == null || openedAt == null || state == null
                || periodStart == null || frozen == null || proposals == null)
            throw new DomainException("episode", "the rescheduling episode data is incomplete");
        if ((state == EpisodeState.OPEN) != (closedAt == null && reason == null))
            throw new DomainException("closedAt", "only a closed episode has a closing instant and a reason");
        if ((frozenAt == null) != (remainingEpisodeMinutes == null)) throw new DomainException("frozenAt", "the freeze instant and the remaining minutes go together");
        proposals = List.copyOf(proposals);
    }

    public boolean isFrozen() {
        return frozenAt != null;
    }

    // HU-COM-020 AC-18.13 (2), AC-20.2: only an open episode with a proposal pending freezes (with no proposal the 24 hours and the episode keep running): the response deadline and the episode
    // remainder freeze together, each with the minutes it had left. A frozen or closed episode does not change.
    public ReschedulingEpisode freeze(Instant at) {
        if (state != EpisodeState.OPEN || isFrozen() || pendingProposal().isEmpty()) return this;
        List<ReschedulingProposal> next = proposals.stream().map(p -> p.frozenAt(at)).toList();
        return new ReschedulingEpisode(id, tenantId, reservationId, originalSalidaId, cancelledAt, openedAt, state, reason, closedAt, periodStart, frozen, next, at,
                Math.max(0, Duration.between(at, episodeLimit()).toMinutes()));
    }

    // AC-20.3: the reactivation resumes both clocks with what remained. The whole minutes spent frozen are added to the total, because the start never changes: the limit moves by it.
    public ReschedulingEpisode resumed(Instant at) {
        if (!isFrozen() || state != EpisodeState.OPEN) return this;
        Duration spent = Duration.ofMinutes(Math.max(0, Duration.between(frozenAt, at).toMinutes()));
        List<ReschedulingProposal> next = proposals.stream().map(p -> p.resumedAt(at)).toList();
        return new ReschedulingEpisode(id, tenantId, reservationId, originalSalidaId, cancelledAt, openedAt, state, reason, closedAt, periodStart, frozen.plus(spent), next, null, null);
    }

    // Every move of a frozen episode that is not the freeze itself starts from its resumed clocks, so a closed episode keeps no freeze behind.
    private ReschedulingEpisode thawed(Instant now) {
        return resumed(now);
    }

    public static ReschedulingEpisode open(UUID id, UUID tenantId, UUID reservationId, UUID originalSalidaId, Instant cancelledAt, Instant openedAt) {
        return new ReschedulingEpisode(id, tenantId, reservationId, originalSalidaId, cancelledAt, openedAt, EpisodeState.OPEN, null, null, cancelledAt,
                Duration.ZERO, List.of());
    }

    public Instant episodeLimit() {
        return ReschedulingDeadlines.episodeLimit(cancelledAt, frozen);
    }

    // The end of the period to propose: 24 hours from the cancellation, or from the last voiding, never past the limit.
    public Instant proposalPeriodEnd() {
        return ReschedulingDeadlines.proposalPeriodEnd(periodStart, episodeLimit());
    }

    public Optional<ReschedulingProposal> pendingProposal() {
        return proposals.stream().filter(ReschedulingProposal::pending).findFirst();
    }

    // The seats are held by the use case; here the proposal is admitted: open episode, no other pending, inside the period that began at the cancellation or the voiding.
    public ReschedulingEpisode propose(UUID proposalId, UUID salidaId, int partySize, UUID by, Instant now, Instant proposedStart) {
        if (state != EpisodeState.OPEN) throw new InvalidStatusTransitionException("the episode is already closed");
        if (pendingProposal().isPresent()) throw new InvalidStatusTransitionException("the episode already has a pending proposal");
        if (now.isBefore(periodStart)) throw new BusinessRuleViolation("a proposal cannot precede the period it belongs to");
        if (!now.isBefore(proposalPeriodEnd())) throw new BusinessRuleViolation("the period to propose has ended");
        ReschedulingProposal proposal = new ReschedulingProposal(proposalId, salidaId, partySize, by, now,
                ReschedulingDeadlines.responseDeadline(now, episodeLimit(), proposedStart), ProposalState.PENDING, null, null);
        List<ReschedulingProposal> next = new ArrayList<>(proposals);
        next.add(proposal);
        return new ReschedulingEpisode(id, tenantId, reservationId, originalSalidaId, cancelledAt, openedAt, state, reason, closedAt, periodStart, frozen, next, frozenAt, remainingEpisodeMinutes);
    }

    // GAP-008, AC-18.14: accept or reject is the whole answer, once, before the response deadline. Accepting closes the episode as RESCHEDULED (reason ACCEPTED); rejecting closes it as
    // CANCELLED (reason REJECTED_BY_CUSTOMER) and the use case cancels the reservation. The instant of an outside answer cannot be in the future nor before the notification.
    public ReschedulingEpisode answer(ProposalAnswer answer, Instant now) {
        if (isFrozen()) return thawed(now).answer(answer, now);
        ReschedulingProposal pending = pendingProposal().orElseThrow(() -> new InvalidStatusTransitionException("the episode has no pending proposal: it was answered, voided or expired"));
        if (!now.isBefore(pending.responseDeadlineAt())) throw new BusinessRuleViolation("the response deadline has passed: the expiry applies");
        ProposalAnswer stamped = answer.stampedAt(now);
        if (stamped.answeredAt().isAfter(now) || stamped.answeredAt().isBefore(pending.proposedAt()))
            throw new DomainException("answeredAt", "the answer cannot be in the future nor before the notification");
        List<ReschedulingProposal> next = new ArrayList<>(proposals);
        next.set(next.indexOf(pending), pending.closed(stamped.accepted() ? ProposalState.ACCEPTED : ProposalState.REJECTED, now, stamped));
        return new ReschedulingEpisode(id, tenantId, reservationId, originalSalidaId, cancelledAt, openedAt,
                stamped.accepted() ? EpisodeState.RESCHEDULED : EpisodeState.CANCELLED, stamped.accepted() ? ClosingReason.ACCEPTED : ClosingReason.REJECTED_BY_CUSTOMER, now,
                periodStart, frozen, next);
    }

    // RN-06, AC-18.7: the operator declares there is no alternative, only before any proposal exists (a voided proposal is still a proposal): the episode closes cancelled.
    public ReschedulingEpisode declareNoAlternative(Instant now) {
        requireOpen();
        if (!proposals.isEmpty()) throw new BusinessRuleViolation("no alternative can be declared once a proposal exists");
        return closedAs(ClosingReason.NO_ALTERNATIVE, now, proposals);
    }

    // RN-09, AC-18.10, AC-18.11: applies exactly one outcome of what is due: the 96-hour limit, the response deadline of the pending proposal (which expires) or the 24 hours to propose. When
    // nothing is due it answers that nothing is due. The episode limit wins over a response deadline that is the episode remainder.
    public ReschedulingEpisode expire(Instant now) {
        requireOpen();
        if (isFrozen()) throw new BusinessRuleViolation("nothing is due: the clocks of the episode are frozen");
        Optional<ReschedulingProposal> pending = pendingProposal();
        if (!now.isBefore(episodeLimit())) return closedAs(ClosingReason.EPISODE_LIMIT_REACHED, now, expiredWith(pending, now));
        if (pending.isPresent() && !now.isBefore(pending.get().responseDeadlineAt())) return closedAs(ClosingReason.RESPONSE_DEADLINE_EXPIRED, now, expiredWith(pending, now));
        if (pending.isEmpty() && !now.isBefore(proposalPeriodEnd())) return closedAs(ClosingReason.PROPOSAL_PERIOD_EXPIRED, now, proposals);
        throw new BusinessRuleViolation("nothing is due: no clock of the episode has run out");
    }

    // AC-18.13 (I1): the proposed departure was cancelled while the tenant is inactive: the proposal is void and the episode is cancelled at once, with no new period.
    public ReschedulingEpisode voidAndCancel(Instant now) {
        requireOpen();
        if (isFrozen()) return thawed(now).voidAndCancel(now);
        ReschedulingProposal pending = pendingProposal().orElseThrow(() -> new InvalidStatusTransitionException("the episode has no pending proposal"));
        List<ReschedulingProposal> next = new ArrayList<>(proposals);
        next.set(next.indexOf(pending), pending.closed(ProposalState.VOID, now, null));
        return closedAs(ClosingReason.PROPOSED_DEPARTURE_CANCELLED_WHILE_INACTIVE, now, next);
    }

    private void requireOpen() {
        if (state != EpisodeState.OPEN) throw new InvalidStatusTransitionException("the episode is already closed");
    }

    private List<ReschedulingProposal> expiredWith(Optional<ReschedulingProposal> pending, Instant now) {
        List<ReschedulingProposal> next = new ArrayList<>(proposals);
        pending.ifPresent(p -> next.set(next.indexOf(p), p.closed(ProposalState.EXPIRED, now, null)));
        return next;
    }

    private ReschedulingEpisode closedAs(ClosingReason why, Instant now, List<ReschedulingProposal> next) {
        return new ReschedulingEpisode(id, tenantId, reservationId, originalSalidaId, cancelledAt, openedAt, EpisodeState.CANCELLED, why, now, periodStart, frozen, next);
    }

    // The proposed departure was cancelled: the proposal is void (not a rejection) and a new period of the lowest of 24 hours and the remainder starts at that instant.
    public ReschedulingEpisode voidPending(Instant now) {
        if (isFrozen()) return thawed(now).voidPending(now);
        ReschedulingProposal pending = pendingProposal().orElseThrow(() -> new InvalidStatusTransitionException("the episode has no pending proposal"));
        List<ReschedulingProposal> next = new ArrayList<>(proposals);
        next.set(next.indexOf(pending), pending.closed(ProposalState.VOID, now, null));
        return new ReschedulingEpisode(id, tenantId, reservationId, originalSalidaId, cancelledAt, openedAt, state, reason, closedAt, now, frozen, next);
    }
}
