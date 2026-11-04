package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import java.util.Optional;
import java.util.UUID;

// HU-COM-013 RN-10: the settlement record, written once with the cancellation and never edited or deleted (table
// reservation_cancellation of the db). Another tenant's reservation reads as empty.
public interface CancellationRecords {

    void save(UUID tenantId, CancellationRecord record);

    Optional<CancellationRecord> find(UUID tenantId, UUID reservationId);
}
