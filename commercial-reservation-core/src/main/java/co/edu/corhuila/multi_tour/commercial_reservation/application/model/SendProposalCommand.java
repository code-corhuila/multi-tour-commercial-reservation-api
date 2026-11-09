package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.UUID;

// HU-COM-018 AC-18.9: who proposes which departure for which episode. The tenant and the actor come from the token, never from the body.
public record SendProposalCommand(UUID tenantId, UUID actorId, Role role, UUID episodeId, UUID proposedSalidaId) {

    public SendProposalCommand {
        if (tenantId == null || actorId == null || role == null || episodeId == null) throw new DomainException("proposal", "the tenant, the actor, the role and the episode are required");
        if (proposedSalidaId == null) throw new DomainException("proposedSalidaId", "the proposed departure is required");
    }
}
