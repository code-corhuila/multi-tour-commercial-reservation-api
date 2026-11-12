package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// HU-COM-018 plan D-03: one proposal of a new departure for the party of the reservation, with the response deadline fixed when it was notified. Immutable; it keeps
// its closing instant once it is no longer pending. The answer, once given, stays on the proposal.
public record ReschedulingProposal(UUID id, UUID salidaId, int partySize, UUID proposedBy, Instant proposedAt, Instant responseDeadlineAt, ProposalState state,
        Instant closedAt, ProposalAnswer answer, Long remainingResponseMinutes) {

    public ReschedulingProposal(UUID id, UUID salidaId, int partySize, UUID proposedBy, Instant proposedAt, Instant responseDeadlineAt, ProposalState state, Instant closedAt, ProposalAnswer answer) {
        this(id, salidaId, partySize, proposedBy, proposedAt, responseDeadlineAt, state, closedAt, answer, null);
    }

    public ReschedulingProposal {
        if (id == null || salidaId == null || proposedBy == null || proposedAt == null || responseDeadlineAt == null || state == null)
            throw new DomainException("proposal", "the proposal data is incomplete");
        if (partySize <= 0) throw new DomainException("partySize", "a proposal is for at least one traveller");
        if ((state == ProposalState.PENDING) != (closedAt == null)) throw new DomainException("closedAt", "only a closed proposal has a closing instant");
        if (remainingResponseMinutes != null && (state != ProposalState.PENDING || remainingResponseMinutes < 0)) throw new DomainException("remaining", "only a pending proposal keeps remaining minutes, never negative");
    }

    public boolean pending() {
        return state == ProposalState.PENDING;
    }

    ReschedulingProposal closed(ProposalState to, Instant at, ProposalAnswer answered) {
        if (!pending()) throw new InvalidStatusTransitionException("the proposal is already " + state);
        return new ReschedulingProposal(id, salidaId, partySize, proposedBy, proposedAt, responseDeadlineAt, to, at, answered, null);
    }

    // HU-COM-020 AC-18.13: the clock of a pending proposal freezes with the minutes it had left; the deadline is kept so the engine row stays whole.
    ReschedulingProposal frozenAt(Instant at) {
        if (!pending() || remainingResponseMinutes != null) return this;
        return new ReschedulingProposal(id, salidaId, partySize, proposedBy, proposedAt, responseDeadlineAt, state, closedAt, answer, Math.max(0, Duration.between(at, responseDeadlineAt).toMinutes()));
    }

    // It resumes with what remained: the deadline is the instant of the resume plus the remaining minutes.
    ReschedulingProposal resumedAt(Instant at) {
        if (remainingResponseMinutes == null) return this;
        return new ReschedulingProposal(id, salidaId, partySize, proposedBy, proposedAt, at.plus(Duration.ofMinutes(remainingResponseMinutes)), state, closedAt, answer, null);
    }
}
