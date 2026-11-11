package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DuePage;
import java.util.UUID;

// HU-COM-019: the reservations whose year has passed, a page at a time (limit 1 to 100, cursor by reservation id), for the retention job.
public interface ListDueSensitiveData {

    DuePage due(UUID tenantId, int limit, UUID after);
}
