package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import java.util.UUID;

// HU-COM-012 plan D-02: append and read only, no update and no delete (RN-RES-004). The page starts at 1, newest first.
public interface ReservationModifications {

    void append(UUID tenantId, ReservationModificationRecord record);

    ModificationPage page(UUID tenantId, UUID reservationId, int page, int limit);
}
