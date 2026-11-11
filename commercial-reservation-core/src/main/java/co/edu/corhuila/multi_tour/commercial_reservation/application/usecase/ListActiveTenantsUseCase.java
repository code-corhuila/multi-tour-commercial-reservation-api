package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantIdPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListActiveTenants;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ActiveTenantQueries;

public class ListActiveTenantsUseCase implements ListActiveTenants {
    private final ActiveTenantQueries queries;

    public ListActiveTenantsUseCase(ActiveTenantQueries queries) {
        this.queries = queries;
    }

    @Override
    public TenantIdPage list(int page, int limit) {
        return queries.page(page, limit);
    }
}
