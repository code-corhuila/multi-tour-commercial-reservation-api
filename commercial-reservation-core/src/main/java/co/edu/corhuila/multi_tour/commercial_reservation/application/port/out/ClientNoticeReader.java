package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.StoredNotice;
import java.util.Optional;
import java.util.UUID;

// The notice of a tenant by the id of its outbox event. Another tenant's notice, and an event that is not a notice, are absent (AC-15.12).
public interface ClientNoticeReader {

    Optional<StoredNotice> find(UUID tenantId, UUID eventId);
}
