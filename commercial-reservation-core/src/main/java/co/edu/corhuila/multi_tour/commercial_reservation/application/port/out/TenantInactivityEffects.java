package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.time.Instant;
import java.util.UUID;

// HU-COM-020 RN-06: what the inactivation and the reactivation of a tenant do in this service, in the transaction of the copy and the processed event id.
public interface TenantInactivityEffects {

    void inactivated(UUID tenantId, UUID eventId, Instant at);

    void reactivated(UUID tenantId, UUID eventId, Instant at);
}
