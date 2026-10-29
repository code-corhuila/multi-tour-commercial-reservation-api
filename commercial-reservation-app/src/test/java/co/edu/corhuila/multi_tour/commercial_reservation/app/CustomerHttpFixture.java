package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerProfileReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRegisteredOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SensitiveDataAccessOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ServiceRiskReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CustomerRegistered;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataAccessed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the real JDBC adapters are exercised by CustomerPersistenceTest and CustomerHttpPersistenceTest.
@TestConfiguration
class CustomerHttpFixture {
    @Bean @Primary MemoryCustomers memoryCustomers() { return new MemoryCustomers(); }

    static final class MemoryCustomers implements CustomerRepository, CustomerProfileReader, CustomerRegisteredOutbox,
            SensitiveDataAccessOutbox, CustomerTransaction, ServiceRiskReader {
        record Service(UUID tenant, boolean highRisk) {}
        final Map<UUID, Customer> customers = new HashMap<>();
        final Map<UUID, Service> services = new HashMap<>();
        final List<CustomerRegistered> registered = new ArrayList<>();
        final List<SensitiveDataAccessed> accessed = new ArrayList<>();
        int fullReads, profileReads;
        RuntimeException failOnAudit;

        void clear() {
            customers.clear(); services.clear(); registered.clear(); accessed.clear();
            fullReads = 0; profileReads = 0; failOnAudit = null;
        }
        public void save(Customer customer) { customers.put(customer.customerId(), customer); }
        public Optional<Customer> findByTenantAndId(UUID tenant, UUID id) {
            fullReads++;
            return Optional.ofNullable(customers.get(id)).filter(found -> found.tenantId().equals(tenant));
        }
        public Optional<Customer> findOrdinary(UUID tenant, UUID id) {
            profileReads++;
            return Optional.ofNullable(customers.get(id)).filter(found -> found.tenantId().equals(tenant))
                    .map(found -> Customer.restore(found.customerId(), found.tenantId(), found.documentNumber(),
                            found.fullName(), found.birthDateOrAge(), found.contact(), found.createdAt(), found.termsAcceptedAt(), null));
        }
        public Optional<Boolean> isHighRisk(UUID tenant, UUID service) {
            return Optional.ofNullable(services.get(service)).filter(found -> found.tenant().equals(tenant))
                    .map(Service::highRisk);
        }
        public void append(CustomerRegistered event) { registered.add(event); }
        public void append(SensitiveDataAccessed event) {
            if (failOnAudit != null) throw failOnAudit;
            accessed.add(event);
        }
        public <T> T inTransaction(Supplier<T> work) {
            var before = new HashMap<>(customers);
            var events = new ArrayList<>(registered);
            try {
                return work.get();
            } catch (RuntimeException failure) {
                customers.clear(); customers.putAll(before);
                registered.clear(); registered.addAll(events);
                throw failure;
            }
        }
    }
}
