package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.Objects;
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
        if (serviceIds == null || serviceIds.isEmpty())
            throw new DomainException("serviceCoverage", "a selection needs at least one service");
        if (serviceIds.stream().anyMatch(Objects::isNull))
            throw new DomainException("serviceCoverage", "a selected service id cannot be null");
        return new ServiceCoverage(Set.copyOf(serviceIds));
    }

    public boolean isAll() { return serviceIds == null; }
    public Set<UUID> serviceIds() { return serviceIds == null ? Set.of() : serviceIds; }

    @Override public boolean equals(Object other) {
        return other instanceof ServiceCoverage c && Objects.equals(serviceIds, c.serviceIds);
    }
    @Override public int hashCode() { return Objects.hashCode(serviceIds); }
}
