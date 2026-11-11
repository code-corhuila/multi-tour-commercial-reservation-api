package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// HU-COM-018 AC-18.10, AC-18.11; worker job 5: an open episode that has a clock run out, named by its tenant and its identifiers so the worker can ask the expiry of each one in its tenant.
public record DueRescheduling(UUID tenantId, UUID reservationId, UUID episodeId) {
}
