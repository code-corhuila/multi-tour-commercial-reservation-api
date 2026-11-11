package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-010: the answer of a decision names the customer; the controller no longer reads the customer itself.
class ReadCustomerNameTest {

    private static final UUID TENANT = new UUID(10, 0xc1), OTHER = new UUID(10, 0xc2), CUSTOMER = new UUID(10, 0xc3);

    private final Map<String, Customer> stored = new HashMap<>();
    private final CustomerRepository customers = new CustomerRepository() {
        @Override public void save(Customer customer) { stored.put(customer.tenantId() + ":" + customer.customerId(), customer); }
        @Override public Optional<Customer> findByTenantAndId(UUID tenantId, UUID customerId) { return Optional.ofNullable(stored.get(tenantId + ":" + customerId)); }
    };
    private final ReadCustomerNameUseCase read = new ReadCustomerNameUseCase(customers);

    ReadCustomerNameTest() {
        customers.save(Customer.registerLead(CUSTOMER, TENANT, "CC12345", "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("persona@example.invalid", "3000000000"), true, Instant.parse("2026-10-29T10:00:00Z")));
    }

    @Test
    void theNameOfTheCustomerOfTheTenantIsReturned() {
        assertThat(read.nameOf(TENANT, CUSTOMER)).isEqualTo("Persona Ficticia");
    }

    @Test
    void aMissingCustomerOrOneOfAnotherTenantGivesAnEmptyName() {
        assertThat(read.nameOf(OTHER, CUSTOMER)).isEmpty();
        assertThat(read.nameOf(TENANT, new UUID(10, 0xff))).isEmpty();
    }
}
