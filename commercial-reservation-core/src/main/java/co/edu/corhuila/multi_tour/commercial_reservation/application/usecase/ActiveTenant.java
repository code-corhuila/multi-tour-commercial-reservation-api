package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import java.util.UUID;

// AC-07.20 (RN-16): an inactive or unknown tenant quotes and creates nothing; an unknown one is refused too, never assumed active.
public final class ActiveTenant {

    private final TenantDirectory tenants;

    public ActiveTenant(TenantDirectory tenants) {
        this.tenants = tenants;
    }

    public void require(UUID tenantId) {
        boolean active = tenantId != null && tenants.resolve(tenantId.toString())
                .filter(found -> tenantId.equals(found.tenantId())).map(TenantRef::active).orElse(false);
        if (!active) throw new BusinessRuleViolation("the tenant is not active");
    }
}
