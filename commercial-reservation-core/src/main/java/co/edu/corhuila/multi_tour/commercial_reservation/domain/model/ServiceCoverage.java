package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.Set;
import java.util.UUID;

// DP-01: a rule covers every service of the tenant, or a selected set of catalog services; exactly one.
public final class ServiceCoverage {
    private static final ServiceCoverage ALL = new ServiceCoverage(null);
    private final Set<UUID> serviceIds;

    private ServiceCoverage(Set<UUID> serviceIds) {
        this.serviceIds = serviceIds;
    }

    public static ServiceCoverage all() {
        return ALL;
    }

    public static ServiceCoverage of(Set<UUID> serviceIds) {
        return new ServiceCoverage(serviceIds);
    }

    public boolean isAll() { return serviceIds == null; }
    public Set<UUID> serviceIds() { return serviceIds == null ? Set.of() : serviceIds; }

    @Override public boolean equals(Object other) {
        return other instanceof ServiceCoverage c && java.util.Objects.equals(serviceIds, c.serviceIds);
    }
    @Override public int hashCode() { return java.util.Objects.hashCode(serviceIds); }
}
