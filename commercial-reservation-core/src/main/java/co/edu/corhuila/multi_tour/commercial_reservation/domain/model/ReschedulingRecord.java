package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// HU-COM-018 RN-01, plan D-04: the agreed change of a rescheduling, kept for ever (never edited). The same reservation moved to the agreed departure; linkedReservationId has no writer yet
// (a new main service or condition is not built, DP-02). approvedBy is the account of whoever answered or recorded the acceptance (DP-D18-4).
public record ReschedulingRecord(UUID id, UUID tenantId, UUID episodeId, UUID originalReservationId, UUID linkedReservationId, UUID agreedSalidaId, Instant agreedStartAt, UUID approvedBy,
        Instant recordedAt) {

    public ReschedulingRecord {
        if (id == null || tenantId == null || episodeId == null || originalReservationId == null || agreedSalidaId == null || agreedStartAt == null || approvedBy == null || recordedAt == null)
            throw new DomainException("record", "the rescheduling record data is incomplete");
        if (originalReservationId.equals(linkedReservationId)) throw new DomainException("linkedReservationId", "the linked reservation cannot be the original");
    }
}
