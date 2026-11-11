package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// HU-COM-018 plan D-03: one proposal of a new departure for the party of the reservation, with the response deadline fixed when it was notified. Immutable; it keeps
// its closing instant once it is no longer pending. The answer, once given, stays on the proposal.
public record ReschedulingProposal(UUID id, UUID salidaId, int partySize, UUID proposedBy, Instant proposedAt, Instant responseDeadlineAt, ProposalState state,
        Instant closedAt, ProposalAnswer answer) {

    public ReschedulingProposal {
        if (id == null || salidaId == null || proposedBy == null || proposedAt == null || responseDeadlineAt == null || state == null)
            throw new DomainException("proposal", "the proposal data is incomplete");
        if (partySize <= 0) throw new DomainException("partySize", "a proposal is for at least one traveller");
        if ((state == ProposalState.PENDING) != (closedAt == null)) throw new DomainException("closedAt", "only a closed proposal has a closing instant");
    }

    public boolean pending() {
        return state == ProposalState.PENDING;
    }

    ReschedulingProposal closed(ProposalState to, Instant at, ProposalAnswer answered) {
        if (!pending()) throw new InvalidStatusTransitionException("the proposal is already " + state);
        return new ReschedulingProposal(id, salidaId, partySize, proposedBy, proposedAt, responseDeadlineAt, to, at, answered);
    }
}
