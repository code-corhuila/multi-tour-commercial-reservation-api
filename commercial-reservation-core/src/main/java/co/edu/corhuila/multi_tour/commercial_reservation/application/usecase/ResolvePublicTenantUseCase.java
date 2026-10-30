package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ResolvePublicTenant;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import java.util.UUID;

public final class ResolvePublicTenantUseCase implements ResolvePublicTenant {

    private final TenantDirectory directory;

    public ResolvePublicTenantUseCase(TenantDirectory directory) {
        this.directory = directory;
    }

    @Override
    public UUID resolve(String identifier) {
        return PublicTenants.require(directory, identifier);
    }
}
