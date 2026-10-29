package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import java.util.Optional;

// Public identifier to tenant (plan D-03, DD-2). The first adapter reads the identifier as the tenant UUID.
public interface TenantDirectory {

    Optional<TenantRef> resolve(String identifier);
}
