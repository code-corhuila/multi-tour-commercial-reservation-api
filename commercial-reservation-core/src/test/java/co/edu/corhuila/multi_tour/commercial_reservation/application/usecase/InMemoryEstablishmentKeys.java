package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentRegistration;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

// The atomic key boundary includes the callback's state and event writes, not just its key map.
class InMemoryEstablishmentKeys implements AssociatedEstablishmentKeys {
    private record Key(UUID tenant, String value) {}
    final Map<Key, UUID> entries = new HashMap<>();
    final InMemoryAssociatedEstablishmentRepository repository;
    boolean failAfterCreate;

    InMemoryEstablishmentKeys(InMemoryAssociatedEstablishmentRepository repository) { this.repository = repository; }

    @Override public synchronized EstablishmentRegistration register(UUID tenant, String key,
            Supplier<AssociatedEstablishment> create, Function<UUID, AssociatedEstablishment> lookup) {
        var scoped = new Key(tenant, key);
        if (entries.containsKey(scoped)) return new EstablishmentRegistration(lookup.apply(entries.get(scoped)), false);
        var rows = new LinkedHashMap<>(repository.rows);
        var events = new ArrayList<>(repository.outbox);
        int saves = repository.saves;
        try {
            var item = create.get();
            if (failAfterCreate) { failAfterCreate = false; throw new IllegalStateException("key write rejected"); }
            entries.put(scoped, item.associationId());
            return new EstablishmentRegistration(item, true);
        } catch (RuntimeException failure) {
            repository.rows.clear(); repository.rows.putAll(rows);
            repository.outbox.clear(); repository.outbox.addAll(events); repository.saves = saves;
            throw failure;
        }
    }
}
