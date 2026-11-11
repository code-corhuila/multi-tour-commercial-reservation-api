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
        ClosingReason reason, Instant closedAt, Instant periodStart, Duration frozen, List<ReschedulingProposal> proposals) {

    public ReschedulingEpisode {
        if (id == null || tenantId == null || reservationId == null || originalSalidaId == null || cancelledAt == null || openedAt == null || state == null
                || periodStart == null || frozen == null || proposals == null)
            throw new DomainException("episode", "the rescheduling episode data is incomplete");
        if ((state == EpisodeState.OPEN) != (closedAt == null && reason == null))
            throw new DomainException("closedAt", "only a closed episode has a closing instant and a reason");
        proposals = List.copyOf(proposals);
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
        return new ReschedulingEpisode(id, tenantId, reservationId, originalSalidaId, cancelledAt, openedAt, state, reason, closedAt, periodStart, frozen, next);
    }

    // GAP-008, AC-18.14: accept or reject is the whole answer, once, before the response deadline. Accepting closes the episode as RESCHEDULED (reason ACCEPTED); rejecting closes it as
    // CANCELLED (reason REJECTED_BY_CUSTOMER) and the use case cancels the reservation. The instant of an outside answer cannot be in the future nor before the notification.
    public ReschedulingEpisode answer(ProposalAnswer answer, Instant now) {
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
        Optional<ReschedulingProposal> pending = pendingProposal();
        if (!now.isBefore(episodeLimit())) return closedAs(ClosingReason.EPISODE_LIMIT_REACHED, now, expiredWith(pending, now));
        if (pending.isPresent() && !now.isBefore(pending.get().responseDeadlineAt())) return closedAs(ClosingReason.RESPONSE_DEADLINE_EXPIRED, now, expiredWith(pending, now));
        if (pending.isEmpty() && !now.isBefore(proposalPeriodEnd())) return closedAs(ClosingReason.PROPOSAL_PERIOD_EXPIRED, now, proposals);
        throw new BusinessRuleViolation("nothing is due: no clock of the episode has run out");
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
        ReschedulingProposal pending = pendingProposal().orElseThrow(() -> new InvalidStatusTransitionException("the episode has no pending proposal"));
        List<ReschedulingProposal> next = new ArrayList<>(proposals);
        next.set(next.indexOf(pending), pending.closed(ProposalState.VOID, now, null));
        return new ReschedulingEpisode(id, tenantId, reservationId, originalSalidaId, cancelledAt, openedAt, state, reason, closedAt, now, frozen, next);
    }
}
