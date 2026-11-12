package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SendProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RespondToReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.SendReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

// HU-COM-018 AC-18.6, AC-18.17, RNF-06: the key is registered last, in the same transaction as the write, so a refusal can be asked again with the same key. A repeat answers the stored
// episode (200; the first time 201 for a proposal and an outside answer); a key lost in a race discards the second write and answers the first. A key belongs to one episode, and the stored
// episode is read again through the ownership of the caller, so another customer cannot read an episode with a key they did not use.
public class IdempotentReschedulingWrites {
    public record Outcome(ReschedulingEpisode episode, boolean created) {}

    private final SendReschedulingProposal proposals;
    private final RespondToReschedulingProposal responses;
    private final IdempotentWrites writes;

    public IdempotentReschedulingWrites(SendReschedulingProposal proposals, RespondToReschedulingProposal responses, IdempotentWrites writes) {
        this.proposals = proposals;
        this.responses = responses;
        this.writes = writes;
    }

    public Outcome propose(PaymentActor actor, String key, UUID episodeId, UUID proposedDepartureId) {
        return once(actor, key, "rescheduling-proposal", episodeId, () -> proposals.send(new SendProposalCommand(actor.tenantId(), actor.actorId(),
                actor.role(), episodeId, proposedDepartureId)),
                found -> found.proposals().stream().anyMatch(p -> p.salidaId().equals(proposedDepartureId)));
    }

    public Outcome answer(PaymentActor actor, String key, UUID episodeId, UUID proposalId, boolean accepted) {
        return once(actor, key, "rescheduling-answer", episodeId, () -> responses.answerAsCustomer(actor, episodeId, proposalId, accepted), found -> answered(found, proposalId));
    }

    public Outcome recordOutside(PaymentActor actor, String key, UUID episodeId, UUID proposalId, boolean accepted, String channel, String evidence, Instant answeredAt) {
        return once(actor, key, "rescheduling-external-answer", episodeId, () -> responses.recordOutside(actor, episodeId, proposalId, accepted, channel, evidence, answeredAt),
                found -> answered(found, proposalId));
    }

    private static boolean answered(ReschedulingEpisode found, UUID proposalId) {
        return found.proposals().stream().anyMatch(p -> p.id().equals(proposalId) && !p.pending());
    }

    private Outcome once(PaymentActor actor, String key, String type, UUID episodeId, Supplier<ReschedulingEpisode> act, Predicate<ReschedulingEpisode> mine) {
        var done = writes.run(actor.tenantId(), key, type, act, ReschedulingEpisode::id, id -> replay(actor, id, episodeId, mine));
        return new Outcome(done.value(), done.created());
    }

    private ReschedulingEpisode replay(PaymentActor actor, UUID stored, UUID episodeId, Predicate<ReschedulingEpisode> mine) {
        ReschedulingEpisode found = responses.visibleTo(actor, stored);
        if (!stored.equals(episodeId) || !mine.test(found)) throw new BusinessRuleViolation("the Idempotency-Key was already used by another request");
        return found;
    }
}
