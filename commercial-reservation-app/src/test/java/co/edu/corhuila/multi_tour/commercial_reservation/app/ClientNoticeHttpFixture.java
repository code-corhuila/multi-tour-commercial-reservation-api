package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.StoredNotice;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC reader of the notice is exercised against the real engine in its own test.
@TestConfiguration
class ClientNoticeHttpFixture {
    @Bean @Primary MemoryNotices memoryNotices() { return new MemoryNotices(); }

    static final class MemoryNotices implements ClientNoticeReader {
        final Map<UUID, StoredNotice> notices = new HashMap<>();

        void clear() { notices.clear(); }
        public Optional<StoredNotice> find(UUID tenantId, UUID eventId) {
            return Optional.ofNullable(notices.get(eventId)).filter(found -> found.tenantId().equals(tenantId));
        }
    }
}
