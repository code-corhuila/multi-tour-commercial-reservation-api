package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.time.Instant;
import java.util.UUID;

// Writes the initial payment deadline row of a new reservation, in the transaction of the creation (HU-COM-009 owns the table).
public interface InitialDeadlineRecorder {
    void record(UUID tenantId, UUID reservationId, Instant dueAt);
}
