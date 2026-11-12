package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantIdPage;

// The tenants of the local status copy whose status is ACTIVE, ordered by id so that pages never repeat. A failure is thrown.
public interface ActiveTenantQueries {
    TenantIdPage page(int page, int limit);
}
