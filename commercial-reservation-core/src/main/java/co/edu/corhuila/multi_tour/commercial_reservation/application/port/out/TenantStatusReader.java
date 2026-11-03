package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.util.Optional;
import java.util.UUID;

// The status of a tenant for the authentication filter (plan D-05): one read by primary key. Empty means no copy yet
// (DP-TSC-05). A failure is thrown, never answered as a value, so a broken lookup cannot let a token through.
public interface TenantStatusReader {
    Optional<TenantStatus> statusOf(UUID tenantId);
}
