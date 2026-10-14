package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.*;
import java.util.*;

// Detached copies keep unsaved changes from leaking into later reads, like a transactional store.
class InMemoryAssociatedEstablishmentRepository implements AssociatedEstablishmentRepository {
    final Map<UUID, AssociatedEstablishment> rows = new LinkedHashMap<>();
    final List<EstablishmentAssociationEvent> outbox = new ArrayList<>();
    boolean failNextSave;
    int saves;

    @Override public void save(AssociatedEstablishment item) {
        if (failNextSave) { failNextSave = false; throw new IllegalStateException("save rejected"); }
        rows.put(item.associationId(), copy(item));
        outbox.addAll(item.pullEvents());
        saves++;
    }

    @Override public Optional<AssociatedEstablishment> findByTenantAndId(UUID tenant, UUID id) {
        return Optional.ofNullable(rows.get(id)).filter(item -> item.tenantId().equals(tenant))
                .map(InMemoryAssociatedEstablishmentRepository::copy);
    }

    @Override public EstablishmentPage listByTenant(UUID tenant, Optional<AssociationStatus> status,
            Optional<EstablishmentType> type, int page, int limit) {
        var matching = rows.values().stream().filter(item -> item.tenantId().equals(tenant))
                .filter(item -> status.isEmpty() || item.associationStatus() == status.get())
                .filter(item -> type.isEmpty() || item.establishmentType() == type.get())
                .sorted(Comparator.comparing(AssociatedEstablishment::createdAt)
                        .thenComparing(item -> item.associationId().toString()).reversed()).toList();
        return new EstablishmentPage(matching.stream().skip((page - 1L) * limit).limit(limit)
                .map(InMemoryAssociatedEstablishmentRepository::copy).toList(), matching.size());
    }

    void plant(AssociatedEstablishment item) { rows.put(item.associationId(), copy(item)); }
    static AssociatedEstablishment copy(AssociatedEstablishment item) {
        return AssociatedEstablishment.restore(item.associationId(), item.tenantId(), item.establishmentType(),
                item.commercialName(), item.commercialInformation(), item.imageReference(), item.associationStatus(),
                item.createdAt(), item.updatedAt());
    }
}
