package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.*;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.*;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.*;
import java.util.*;
import java.util.function.*;

// One monitor is the transaction boundary of this volatile adapter, including key and image writes.
public final class InMemoryEstablishments implements AssociatedEstablishmentRepository,
        AssociatedEstablishmentKeys, EstablishmentImages {
    private record Key(UUID tenant, String value) {}
    private record ImageKey(UUID tenant, String reference) {}
    private final Map<UUID, AssociatedEstablishment> rows = new HashMap<>();
    private final Map<Key, UUID> keys = new HashMap<>();
    private final Map<ImageKey, Image> images = new HashMap<>();
    private final List<EstablishmentAssociationEvent> outbox = new ArrayList<>();

    @Override public synchronized void save(AssociatedEstablishment item) {
        rows.put(item.associationId(), copy(item));
        outbox.addAll(item.pullEvents());
    }
    @Override public synchronized Optional<AssociatedEstablishment> findByTenantAndId(UUID tenant, UUID id) {
        return Optional.ofNullable(rows.get(id)).filter(item -> item.tenantId().equals(tenant)).map(InMemoryEstablishments::copy);
    }
    @Override public synchronized EstablishmentPage listByTenant(UUID tenant, Optional<AssociationStatus> status,
            Optional<EstablishmentType> type, int page, int limit) {
        var matching = rows.values().stream().filter(item -> item.tenantId().equals(tenant))
                .filter(item -> status.isEmpty() || item.associationStatus() == status.get())
                .filter(item -> type.isEmpty() || item.establishmentType() == type.get())
                .sorted(Comparator.comparing(AssociatedEstablishment::createdAt)
                        .thenComparing(item -> item.associationId().toString()).reversed()).toList();
        return new EstablishmentPage(matching.stream().skip((page - 1L) * limit).limit(limit)
                .map(InMemoryEstablishments::copy).toList(), matching.size());
    }
    @Override public synchronized EstablishmentRegistration register(UUID tenant, String key,
            Supplier<AssociatedEstablishment> create, Function<UUID, AssociatedEstablishment> lookup) {
        return atomic(() -> {
            var scoped = new Key(tenant, key);
            if (keys.containsKey(scoped)) return new EstablishmentRegistration(lookup.apply(keys.get(scoped)), false);
            var item = create.get();
            keys.put(scoped, item.associationId());
            return new EstablishmentRegistration(item, true);
        });
    }
    @Override public synchronized EstablishmentRegistration register(byte[] bytes, String mediaType,
            Supplier<EstablishmentRegistration> registration) {
        return atomic(() -> {
            var result = registration.get();
            if (result.created()) images.put(new ImageKey(result.association().tenantId(),
                    result.association().imageReference()), new Image(bytes, mediaType));
            return result;
        });
    }
    @Override public synchronized Image read(UUID tenant, String reference) {
        var image = images.get(new ImageKey(tenant, reference));
        if (image == null) throw new AssociatedEstablishmentNotFoundException();
        return image;
    }
    private <T> T atomic(Supplier<T> operation) {
        var oldRows = new HashMap<>(rows);
        var oldKeys = new HashMap<>(keys);
        var oldImages = new HashMap<>(images);
        var oldEvents = new ArrayList<>(outbox);
        try { return operation.get(); }
        catch (RuntimeException failure) {
            rows.clear(); rows.putAll(oldRows); keys.clear(); keys.putAll(oldKeys);
            images.clear(); images.putAll(oldImages); outbox.clear(); outbox.addAll(oldEvents);
            throw failure;
        }
    }
    private static AssociatedEstablishment copy(AssociatedEstablishment item) {
        return AssociatedEstablishment.restore(item.associationId(), item.tenantId(), item.establishmentType(),
                item.commercialName(), item.commercialInformation(), item.imageReference(), item.associationStatus(),
                item.createdAt(), item.updatedAt());
    }
}
