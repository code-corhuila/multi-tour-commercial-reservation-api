package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory.InMemoryEstablishments;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CollaboratorEditingPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Comparator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@TestConfiguration
@org.springframework.context.annotation.Import({TenantStatusHttpFixture.class, TenantDirectoryHttpFixture.class})
class CatalogHttpFixture {
    @Bean @Primary InMemoryEstablishments memoryEstablishments() { return new InMemoryEstablishments(); }
    @Bean @Primary MemoryCatalog memoryCatalog() { return new MemoryCatalog(); }
    @Bean @Primary MemoryKeys memoryKeys() { return new MemoryKeys(); }
    @Bean @Primary MemoryCollaboratorPolicy memoryCollaboratorPolicy() { return new MemoryCollaboratorPolicy(); }
    @Bean @Primary SalidaCapacityReader memoryCapacity(MemoryCatalog catalog) {
        return (tenant, service) -> catalog.findByTenantAndId(tenant, service)
                .map(item -> catalog.held.getOrDefault(service, List.of()).stream()
                        .mapToInt(Integer::intValue).max().orElse(0)).orElse(0);
    }
    @Bean @Primary Clock catalogClock() { return Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC); }
    @Bean @Primary PlatformTransactionManager memoryTransactions() {
        // This fake only lets HTTP tests run; a PostgreSQL test must prove rollback.
        return new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object transaction, TransactionDefinition definition) {}
            protected void doCommit(DefaultTransactionStatus status) {}
            protected void doRollback(DefaultTransactionStatus status) {}
        };
    }

    static final class MemoryCatalog implements CatalogServiceRepository {
        final Map<UUID, List<Integer>> held = new HashMap<>();
        final Map<UUID, CatalogService> items = new HashMap<>();
        public void save(CatalogService service) { items.put(service.serviceId(), service); }
        public Optional<CatalogService> findByTenantAndId(UUID tenant, UUID id) {
            return Optional.ofNullable(items.get(id)).filter(service -> service.tenantId().equals(tenant));
        }
        public List<CatalogService> listByTenant(UUID tenant, Optional<CatalogStatus> status) {
            return items.values().stream().filter(service -> service.tenantId().equals(tenant))
                    .filter(service -> status.isEmpty() || service.catalogStatus() == status.get()).toList();
        }
        public CatalogPage pageByTenant(UUID tenant, Optional<CatalogStatus> status,
                Optional<CatalogServiceType> type, int page, int limit) {
            var matching = listByTenant(tenant, status).stream()
                    .filter(service -> type.isEmpty() || service.serviceType() == type.get())
                    .sorted(Comparator.comparing(CatalogService::createdAt)
                            .thenComparing(service -> service.serviceId().toString()).reversed()).toList();
            return new CatalogPage(matching.stream().skip((page - 1L) * limit).limit(limit).toList(), matching.size());
        }
    }

    // The tenant that enabled the Operational Collaborator is a test input: no source for it exists yet (DD-19).
    static final class MemoryCollaboratorPolicy implements CollaboratorEditingPolicy {
        private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
        void enable(UUID tenant) { enabled.add(tenant); }
        public boolean allowsDescriptiveUpdates(UUID tenantId) { return enabled.contains(tenantId); }
    }

    static final class MemoryKeys implements IdempotencyKeys {
        record Key(UUID tenant, String value) {}
        final Map<Key, UUID> keys = new HashMap<>();
        public boolean tryRegister(UUID tenant, String key, String type, UUID id) {
            return keys.putIfAbsent(new Key(tenant, key), id) == null;
        }
        public Optional<UUID> findResourceId(UUID tenant, String key) {
            return Optional.ofNullable(keys.get(new Key(tenant, key)));
        }
    }
}
