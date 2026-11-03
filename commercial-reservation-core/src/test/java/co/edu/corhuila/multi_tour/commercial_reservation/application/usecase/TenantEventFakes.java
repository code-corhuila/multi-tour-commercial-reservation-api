package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatusCopy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// One fake for the copy, the processed ids and the transaction around them: a failing transaction restores both.
final class TenantEventFakes implements TenantStatusStore, ProcessedEvents, TenantEventTransaction {

    final Map<UUID, TenantStatusCopy> copies = new HashMap<>();
    final Set<UUID> processed = new HashSet<>();
    RuntimeException failAfterRecord;

    @Override
    public Optional<TenantStatusCopy> findForUpdate(UUID tenantId) {
        return Optional.ofNullable(copies.get(tenantId));
    }

    @Override
    public boolean insertIfAbsent(TenantStatusCopy copy) {
        return copies.putIfAbsent(copy.tenantId(), copy) == null;
    }

    @Override
    public boolean moveForward(TenantStatusCopy copy) {
        TenantStatusCopy kept = copies.get(copy.tenantId());
        if (kept == null || !kept.lastEventAt().isBefore(copy.lastEventAt())) return false;
        copies.put(copy.tenantId(), copy);
        return true;
    }

    @Override
    public boolean wasProcessed(UUID eventId) {
        return processed.contains(eventId);
    }

    @Override
    public void record(UUID eventId) {
        processed.add(eventId);
        if (failAfterRecord != null) throw failAfterRecord;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        Map<UUID, TenantStatusCopy> copiesBefore = new HashMap<>(copies);
        Set<UUID> processedBefore = new HashSet<>(processed);
        try {
            return work.get();
        } catch (RuntimeException failure) {
            copies.clear(); copies.putAll(copiesBefore);
            processed.clear(); processed.addAll(processedBefore);
            throw failure;
        }
    }
}
