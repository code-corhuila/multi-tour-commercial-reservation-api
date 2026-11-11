package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AnswerProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.util.UUID;

// HU-COM-018 AC-18.7, AC-18.10, AC-18.11: the ways an episode ends cancelled, with 100 % of the validated values and no penalty: the rejection, the declaration of no alternative and the expiry.
public interface ConcludeRescheduling {

    ReschedulingEpisode reject(AnswerProposalCommand command);

    ReschedulingEpisode declareNoAlternative(UUID tenantId, UUID actorId, Role role, UUID episodeId);

    // Called by job 5 through the internal route; answers that nothing is due when no clock has run out.
    ReschedulingEpisode expire(UUID tenantId, UUID episodeId);
}
