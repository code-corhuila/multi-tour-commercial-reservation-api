package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

public record EstablishmentAssociationReactivated(UUID tenantId, UUID associationId, UUID actorId,
        Instant occurredAt) implements EstablishmentAssociationEvent {}
