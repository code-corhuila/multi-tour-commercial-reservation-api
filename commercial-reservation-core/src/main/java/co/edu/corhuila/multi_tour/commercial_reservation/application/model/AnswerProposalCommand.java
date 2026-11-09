package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalAnswer;
import java.time.Instant;
import java.util.UUID;

// HU-COM-018 AC-18.6: an answer to the pending proposal of an episode. The answer of the customer carries nothing else than the decision; an outside answer carries channel, evidence and the instant
// it was given. The tenant and the actor come from the token, never from the body; actorCustomerId is the customer of the token (null for staff).
public record AnswerProposalCommand(UUID tenantId, UUID actorId, Role role, UUID actorCustomerId, UUID episodeId, boolean accepted, String channel, String evidence, Instant answeredAt) {

    public AnswerProposalCommand {
        if (tenantId == null || actorId == null || role == null || episodeId == null) throw new DomainException("answer", "the tenant, the actor, the role and the episode are required");
    }

    public static AnswerProposalCommand customer(UUID tenantId, UUID actorId, UUID customerId, UUID episodeId, boolean accepted) {
        return new AnswerProposalCommand(tenantId, actorId, Role.FINAL_CUSTOMER, customerId, episodeId, accepted, null, null, null);
    }

    public static AnswerProposalCommand outside(UUID tenantId, UUID actorId, Role role, UUID episodeId, boolean accepted, String channel, String evidence, Instant answeredAt) {
        return new AnswerProposalCommand(tenantId, actorId, role, null, episodeId, accepted, channel, evidence, answeredAt);
    }

    public ProposalAnswer toAnswer() {
        return role == Role.FINAL_CUSTOMER ? ProposalAnswer.byCustomer(accepted, actorId) : ProposalAnswer.outside(accepted, actorId, channel, evidence, answeredAt);
    }
}
