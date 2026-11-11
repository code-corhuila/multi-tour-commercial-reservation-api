package co.edu.corhuila.multi_tour.commercial_reservation.app;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapters of the tenant copy are exercised against the real engine in their own tests.
@TestConfiguration
class TenantEventHttpFixture {
    @Bean @Primary MemoryTenants memoryTenants() { return new MemoryTenants(); }



    static final class MemoryTenants implements TenantStatusStore, ProcessedEvents, TenantEventTransaction {
        final Map<UUID, TenantStatusCopy> copies = new HashMap<>();
        final Set<UUID> processed = new HashSet<>();

        void clear() { copies.clear(); processed.clear(); }
        public Optional<TenantStatusCopy> findForUpdate(UUID tenantId) { return Optional.ofNullable(copies.get(tenantId)); }
        public boolean insertIfAbsent(TenantStatusCopy copy) { return copies.putIfAbsent(copy.tenantId(), copy) == null; }
        public boolean moveForward(TenantStatusCopy copy) {
            TenantStatusCopy kept = copies.get(copy.tenantId());
            if (kept == null || !kept.lastEventAt().isBefore(copy.lastEventAt())) return false;
            copies.put(copy.tenantId(), copy);
            return true;
        }
        public boolean wasProcessed(UUID eventId) { return processed.contains(eventId); }
        public void record(UUID eventId) { processed.add(eventId); }
        public <T> T inTransaction(Supplier<T> work) { return work.get(); }
    }
}
