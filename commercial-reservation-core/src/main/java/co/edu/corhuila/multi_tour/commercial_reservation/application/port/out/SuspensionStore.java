package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SuspensionIntervals;
import java.time.Instant;
import java.util.UUID;

// HU-COM-020 RN-14, plan D-02: the append-only history of the periods of inactivity of a tenant (table tenant_suspension). Called inside the transaction of the tenant event only.
public interface SuspensionStore {

    // Opens a period; false when the tenant already has an open one (the event applies once).
    boolean open(UUID id, UUID tenantId, Instant at, UUID openingEventId);

    // Closes the open period of the tenant; false when none is open (a reactivation with nothing open does nothing).
    boolean closeOpen(UUID tenantId, Instant at, UUID closingEventId);

    // The periods of the tenant, for the marks that leave the suspended time out (RN-04).
    SuspensionIntervals intervals(UUID tenantId);
}
