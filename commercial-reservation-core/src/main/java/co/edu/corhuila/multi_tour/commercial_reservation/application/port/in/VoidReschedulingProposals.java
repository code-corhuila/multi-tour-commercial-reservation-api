package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// HU-COM-018 AC-18.12, AC-18.13: the saga tells that a proposed departure was cancelled; every pending proposal for it is void. Returns the episodes that were changed.
public interface VoidReschedulingProposals {
    List<UUID> voidProposals(UUID tenantId, UUID proposedSalidaId, Instant cancelledAt);
}
