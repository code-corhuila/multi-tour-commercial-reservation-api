package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SuspensionIntervals;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC reader is exercised against the real engine in its own test. No cache here, so a test
// that changes a status sees it on the next request; the 5 seconds are proved in CachedTenantStatusReaderTest.
@TestConfiguration
@org.springframework.context.annotation.Import(AccountSessionCutoffHttpFixture.class)
class TenantStatusHttpFixture {
    @Bean @Primary MemoryStatus memoryStatus() { return new MemoryStatus(); }
    // The history of suspensions is read by the review mark of the proofs; the JDBC reader is proved against the real engine in TenantInactivityPersistenceTest.
    @Bean @Primary SuspensionStore noSuspensions() {
        return new SuspensionStore() {
            public boolean open(UUID id, UUID tenantId, java.time.Instant at, UUID openingEventId) { return true; }
            public boolean closeOpen(UUID tenantId, java.time.Instant at, UUID closingEventId) { return false; }
            public SuspensionIntervals intervals(UUID tenantId) { return new SuspensionIntervals(java.util.List.of()); }
        };
    }

    static final class MemoryStatus implements TenantStatusReader {
        final Map<UUID, TenantStatus> statuses = new HashMap<>();
        RuntimeException failure;

        void clear() { statuses.clear(); failure = null; }
        public Optional<TenantStatus> statusOf(UUID tenantId) {
            if (failure != null) throw failure;
            return Optional.ofNullable(statuses.get(tenantId));
        }
    }
}
