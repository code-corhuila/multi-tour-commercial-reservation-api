package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Instant;
import java.util.UUID;

// HU-COM-018 AC-18.6, AC-18.14, AC-18.15, AC-18.18: the answers to the pending proposal of an episode. The Final Customer answers only the proposal of their own reservation; the Administrator
// and the Collaborator record an answer received by an outside channel. A foreign or unknown episode or proposal looks missing. Accepting and rejecting are the use cases of the episode.
public interface RespondToReschedulingProposal {
    ReschedulingEpisode answerAsCustomer(PaymentActor actor, UUID episodeId, UUID proposalId, boolean accepted);

    ReschedulingEpisode recordOutside(PaymentActor actor, UUID episodeId, UUID proposalId, boolean accepted, String channel, String evidence, Instant answeredAt);

    // The episode as the caller may see it (the stored answer of a repeated request): the customer of the reservation, or any staff of the tenant.
    ReschedulingEpisode visibleTo(PaymentActor actor, UUID episodeId);
}
