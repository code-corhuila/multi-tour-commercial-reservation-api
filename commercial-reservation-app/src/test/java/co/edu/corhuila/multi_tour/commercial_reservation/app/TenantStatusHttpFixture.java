package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
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
class TenantStatusHttpFixture {
    @Bean @Primary MemoryStatus memoryStatus() { return new MemoryStatus(); }

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
