package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

public sealed interface EstablishmentAssociationEvent permits EstablishmentAssociationPublished,
        EstablishmentAssociationInactivated, EstablishmentAssociationReactivated {
    UUID tenantId();
    UUID associationId();
    UUID actorId();
    Instant occurredAt();
}
