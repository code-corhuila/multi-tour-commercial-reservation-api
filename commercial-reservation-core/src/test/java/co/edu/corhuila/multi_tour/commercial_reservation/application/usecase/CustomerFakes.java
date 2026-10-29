package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerProfileReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRegisteredOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ServiceRiskReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CustomerRegistered;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// Customers and events share one fake store, so a rollback undoes both (norm 5.3.11).
final class CustomerFakes implements CustomerRepository, CustomerProfileReader, CustomerRegisteredOutbox, CustomerTransaction,
        ServiceRiskReader {
    static final UUID TENANT = new UUID(0, 0xd1), OTHER_TENANT = new UUID(0, 0xd2), ACTOR = new UUID(0, 0xe1);
    static final UUID ORDINARY = new UUID(0, 0xf1), RISKY = new UUID(0, 0xf2), FOREIGN = new UUID(0, 0xf3);
    static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    final Map<String, Customer> customers = new HashMap<>();
    final List<CustomerRegistered> events = new ArrayList<>();
    RuntimeException failOnSave, failOnAppend;
    int fullReads, profileReads;

    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final IdGenerator ids = () -> new UUID(0, 0xa1);

    static CustomerActor actor(CustomerActor.Role role) { return new CustomerActor(TENANT, ACTOR, role); }

    @Override public Optional<Boolean> isHighRisk(UUID tenantId, UUID serviceId) {
        if (!TENANT.equals(tenantId)) return Optional.empty();
        if (ORDINARY.equals(serviceId)) return Optional.of(false);
        if (RISKY.equals(serviceId)) return Optional.of(true);
        return Optional.empty(); // FOREIGN belongs to OTHER_TENANT, so it does not exist for TENANT
    }
    @Override public void save(Customer customer) {
        if (failOnSave != null) throw failOnSave;
        customers.put(customer.tenantId() + "/" + customer.customerId(), customer);
    }
    @Override public Optional<Customer> findByTenantAndId(UUID tenantId, UUID customerId) {
        fullReads++;
        return Optional.ofNullable(customers.get(tenantId + "/" + customerId));
    }
    // The ordinary read never carries the risk data, as the real adapter does not even load it.
    @Override public Optional<Customer> findOrdinary(UUID tenantId, UUID customerId) {
        profileReads++;
        return Optional.ofNullable(customers.get(tenantId + "/" + customerId)).map(found -> Customer.restore(
                found.customerId(), found.tenantId(), found.documentNumber(), found.fullName(),
                found.birthDateOrAge(), found.contact(), found.createdAt(), found.termsAcceptedAt(), null));
    }
    @Override public void append(CustomerRegistered event) {
        if (failOnAppend != null) throw failOnAppend;
        events.add(event);
    }
    @Override public <T> T inTransaction(Supplier<T> work) {
        var before = new HashMap<>(customers);
        var eventsBefore = new ArrayList<>(events);
        try {
            return work.get();
        } catch (RuntimeException e) {
            customers.clear(); customers.putAll(before);
            events.clear(); events.addAll(eventsBefore);
            throw e;
        }
    }
}
