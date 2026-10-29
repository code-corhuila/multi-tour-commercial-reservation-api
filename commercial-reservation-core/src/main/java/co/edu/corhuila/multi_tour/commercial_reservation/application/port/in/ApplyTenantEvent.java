package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantEventOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEvent;

// Applies a tenant event of identity-audit once, moving the copy only forward (RN-TSC-02, -03, -04).
public interface ApplyTenantEvent {
    TenantEventOutcome apply(TenantEvent event);
}
