package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SendProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RespondToReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.SendReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-018 AC-18.6, AC-18.17, RNF-06: the key is registered last, in the same transaction as the write, so a refusal can be asked again with the same key. A repeat answers the stored
// episode (200; the first time 201 for a proposal and an outside answer); a key lost in a race discards the second write and answers the first. A key belongs to one episode, and the stored
// episode is read again through the ownership of the caller, so another customer cannot read an episode with a key they did not use.
public class IdempotentReschedulingWrites {
    public record Outcome(ReschedulingEpisode episode, boolean created) {}

    private final SendReschedulingProposal proposals;
    private final RespondToReschedulingProposal responses;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentReschedulingWrites(SendReschedulingProposal proposals, RespondToReschedulingProposal responses, IdempotencyKeys keys, TransactionTemplate transactions) {
        this.proposals = proposals;
        this.responses = responses;
        this.keys = keys;
        this.transactions = transactions;
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

    private Outcome once(PaymentActor actor, String key, String type, UUID episodeId, Supplier<ReschedulingEpisode> act, java.util.function.Predicate<ReschedulingEpisode> mine) {
        UUID tenant = actor.tenantId();
        Optional<UUID> known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return replay(actor, known.get(), episodeId, mine);
        ReschedulingEpisode[] made = new ReschedulingEpisode[1];
        Boolean lost = transactions.execute(status -> {
            made[0] = act.get();
            if (!keys.tryRegister(tenant, key, type, made[0].id())) {
                status.setRollbackOnly();
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(lost)) return replay(actor, keys.findResourceId(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource")), episodeId, mine);
        return new Outcome(made[0], true);
    }

    private Outcome replay(PaymentActor actor, UUID stored, UUID episodeId, java.util.function.Predicate<ReschedulingEpisode> mine) {
        ReschedulingEpisode found = responses.visibleTo(actor, stored);
        if (!stored.equals(episodeId) || !mine.test(found)) throw new BusinessRuleViolation("the Idempotency-Key was already used by another request");
        return new Outcome(found, false);
    }
}
