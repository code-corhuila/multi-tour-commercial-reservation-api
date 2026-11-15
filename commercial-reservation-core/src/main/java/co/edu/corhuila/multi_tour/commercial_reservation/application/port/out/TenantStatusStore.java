package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatusCopy;
import java.util.Optional;
import java.util.UUID;

// The copy of the tenant status (table tenant_status of the db 021). Called inside TenantEventTransaction only.
public interface TenantStatusStore {

    // Locks the row until the transaction ends, so two events of one tenant are applied one after the other.
    Optional<TenantStatusCopy> findForUpdate(UUID tenantId);

    // False when a row for the tenant appeared meanwhile.
    boolean insertIfAbsent(TenantStatusCopy copy);

    // Writes the copy only when its lastEventAt is newer than the stored one (plan D-02); false when it is not.
    boolean moveForward(TenantStatusCopy copy);
}
