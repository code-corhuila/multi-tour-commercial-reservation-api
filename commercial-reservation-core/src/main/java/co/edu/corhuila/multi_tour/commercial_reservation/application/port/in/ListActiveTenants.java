package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantIdPage;

// What the jobs ask to know which tenants to run for (HU-WRK-001 DP-08): the ids of the active tenants of this domain's copy.
public interface ListActiveTenants {
    TenantIdPage list(int page, int limit);
}
