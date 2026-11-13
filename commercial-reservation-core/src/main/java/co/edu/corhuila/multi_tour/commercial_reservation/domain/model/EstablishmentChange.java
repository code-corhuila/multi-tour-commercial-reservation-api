package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// One line of the trace of an association: what happened, why, who did it and when (PDR RF-018). The reason
// is absent for the registration. The aggregate raises it and the repository stores it with the state.
public record EstablishmentChange(UUID changeId, UUID tenantId, UUID associationId, String commercialName,
        EstablishmentChangeType eventType, String reason, UUID actorId, Instant occurredAt) {}
