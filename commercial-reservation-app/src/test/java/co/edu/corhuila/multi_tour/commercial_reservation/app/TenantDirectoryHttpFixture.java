package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC directory is exercised against the real engine in TenantStatusPersistenceTest. An identifier
// that was not planted is read as an active tenant UUID, which is what the routes of the older tests send.
@TestConfiguration
class TenantDirectoryHttpFixture {
    @Bean @Primary MemoryDirectory memoryDirectory() { return new MemoryDirectory(); }

    static final class MemoryDirectory implements TenantDirectory {
        final Map<String, TenantRef> tenants = new HashMap<>();

        public Optional<TenantRef> resolve(String identifier) {
            if (tenants.containsKey(identifier)) return Optional.of(tenants.get(identifier));
            try {
                return Optional.of(new TenantRef(UUID.fromString(identifier), true));
            } catch (IllegalArgumentException notAUuid) {
                return Optional.empty();
            }
        }
    }
}
