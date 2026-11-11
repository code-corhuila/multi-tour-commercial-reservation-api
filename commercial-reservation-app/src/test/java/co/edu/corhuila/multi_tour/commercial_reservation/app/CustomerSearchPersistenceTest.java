package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.crypto.AesGcmFieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerNameSearch;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// The name search over the real schema with two tenants (api spec 005, F-18 amended; db index idx_customer_tenant_full_name).
// Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CustomerSearchPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private final UUID tenant = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();
    private JdbcCustomerRepository repository;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        repository = new JdbcCustomerRepository(new JdbcTemplate(source),
                new AesGcmFieldCipher("test1", Map.of("test1", Base64.getEncoder().encodeToString(key))));
    }

    private Customer save(UUID owner, String name, boolean risky) {
        var data = risky ? new HighRiskData("ZZ-NEG-FICTICIA", "Contacto Ficticio 000-555-0199", "Ninguna", true) : null;
        var customer = Customer.registerLead(UUID.randomUUID(), owner, "DOC-" + UUID.randomUUID(), name, BirthDateOrAge.ofAge(40),
                new ContactInfo("persona@example.invalid", "3000000000"), true, NOW, risky, data);
        repository.save(customer);
        return customer;
    }

    @Test
    void aTenantFindsOnlyItsOwnCustomersByTheStartOfTheNameIgnoringCase() {
        save(tenant, "Zenaida Ficticia Uno", true);
        save(tenant, "ZENAIDA Ficticia Dos", false);
        save(tenant, "Otra Persona", false);
        save(otherTenant, "Zenaida De Otro Tenant", false);
        List<Customer> found = repository.findByNamePrefix(tenant, "zenaida", null, 10);
        assertThat(found).extracting(Customer::fullName).containsExactly("ZENAIDA Ficticia Dos", "Zenaida Ficticia Uno");
        assertThat(found).allMatch(customer -> customer.tenantId().equals(tenant));
        assertThat(found).allMatch(customer -> customer.highRiskData() == null);
        assertThat(found.get(0).documentNumber().value()).startsWith("DOC-");
        assertThat(found.get(0).contact().email()).isEqualTo("persona@example.invalid");
        assertThat(repository.findByNamePrefix(otherTenant, "zenaida", null, 10)).hasSize(1);
    }

    @Test
    void thePercentUnderscoreAndBackslashOfTheTextAreLiteralAndNeverWildcards() {
        save(tenant, "Wanda Ficticia", false);
        save(tenant, "100% Ficticio", false);
        assertThat(repository.findByNamePrefix(tenant, "w%", null, 10)).isEmpty();
        assertThat(repository.findByNamePrefix(tenant, "w_", null, 10)).isEmpty();
        assertThat(repository.findByNamePrefix(tenant, "wanda\\", null, 10)).isEmpty();
        assertThat(repository.findByNamePrefix(tenant, "100%", null, 10)).hasSize(1);
    }

    @Test
    void theCursorContinuesAfterTheLastRowWithoutRepeatingAndTheLimitIsRespected() {
        for (String name : new String[] {"Xiomara Uno", "Xiomara Dos", "Xiomara Tres"}) save(tenant, name, false);
        var first = repository.findByNamePrefix(tenant, "xiomara", null, 2);
        assertThat(first).hasSize(2);
        var last = first.get(1);
        var rest = repository.findByNamePrefix(tenant, "xiomara",
                new CustomerNameSearch.After(last.fullName().toLowerCase(), last.customerId()), 2);
        assertThat(rest).hasSize(1);
        assertThat(first).extracting(Customer::customerId).doesNotContain(rest.get(0).customerId());
    }

    private static boolean present(String variable) {
        String value = System.getenv(variable);
        return value != null && !value.isBlank();
    }
}
