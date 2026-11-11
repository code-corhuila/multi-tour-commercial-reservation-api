package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogPage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Stores copies, like a database would: a change that is never saved must not show up on the next read.
class InMemoryCatalogServiceRepository implements CatalogServiceRepository {

    private final Map<UUID, CatalogService> rows = new LinkedHashMap<>();
    private final List<CatalogServiceEvent> outbox = new ArrayList<>();

    @Override
    public void save(CatalogService service) {
        outbox.addAll(service.pullEvents());
        rows.put(service.serviceId(), copyOf(service));
    }

    @Override
    public Optional<CatalogService> findByTenantAndId(UUID tenantId, UUID serviceId) {
        return Optional.ofNullable(rows.get(serviceId)).filter(row -> row.tenantId().equals(tenantId))
                .map(InMemoryCatalogServiceRepository::copyOf);
    }

    @Override
    public List<CatalogService> listByTenant(UUID tenantId, Optional<CatalogStatus> status) {
        return rows.values().stream()
                .filter(row -> row.tenantId().equals(tenantId))
                .filter(row -> status.map(wanted -> row.catalogStatus() == wanted).orElse(true))
                .sorted(Comparator.comparing(CatalogService::createdAt).reversed())
                .map(InMemoryCatalogServiceRepository::copyOf)
                .toList();
    }

    @Override
    public CatalogPage pageByTenant(UUID tenantId, Optional<CatalogStatus> status,
            Optional<CatalogServiceType> type, int page, int limit) {
        var matching = listByTenant(tenantId, status).stream()
                .filter(service -> type.isEmpty() || service.serviceType() == type.get())
                .sorted(Comparator.comparing(CatalogService::createdAt)
                        .thenComparing(service -> service.serviceId().toString()).reversed()).toList();
        return new CatalogPage(matching.stream().skip((page - 1L) * limit).limit(limit).toList(), matching.size());
    }

    List<CatalogServiceEvent> outbox() {
        return List.copyOf(outbox);
    }

    void plant(CatalogService stored) {
        rows.put(stored.serviceId(), copyOf(stored));
    }

    static CatalogService copyOf(CatalogService s) {
        return CatalogService.restore(s.serviceId(), s.tenantId(), s.serviceType(), s.commercialName(),
                s.catalogStatus(), s.terms(), s.createdAt(), s.updatedAt());
    }
}
