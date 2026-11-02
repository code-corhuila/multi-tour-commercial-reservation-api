package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.crypto.AesGcmFieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerAccountLink;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// HU-COM-005 AC-05.7: the account link over the real schema. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CustomerAccountLinkPersistenceTest {
    private final UUID tenant = UUID.randomUUID(), otherTenant = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private JdbcCustomerRepository repository;
    private JdbcCustomerAccountLink link;

    @BeforeEach void connect() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null
                && System.getenv("TEST_DATABASE_PASSWORD") != null, "All three TEST_DATABASE variables are required");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD")));
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        repository = new JdbcCustomerRepository(jdbc, new AesGcmFieldCipher("test1", Map.of("test1", Base64.getEncoder().encodeToString(key))));
        link = new JdbcCustomerAccountLink(jdbc);
    }

    private Customer customer(UUID owner) {
        UUID id = UUID.randomUUID();
        return Customer.registerLead(id, owner, "DOC-" + id, "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("enlace." + id + "@example.invalid", "000-555-0100"), true, Instant.parse("2026-11-02T12:00:00Z"));
    }

    private UUID accountOf(UUID customerId) {
        return jdbc.queryForObject("SELECT account_id FROM commercial_reservation.customer WHERE customer_id = ?", UUID.class, customerId);
    }

    @Test void onlyARecordOfTheTenantWithoutAnAccountReceivesTheLink() {
        Customer free = customer(tenant);
        Customer taken = customer(tenant);
        Customer foreign = customer(otherTenant);
        repository.save(free);
        repository.save(taken.linkedTo(UUID.randomUUID()));
        repository.save(foreign);
        UUID account = UUID.randomUUID(), kept = accountOf(taken.customerId());

        assertThat(link.linkIfUnlinked(tenant, free.customerId(), account)).isTrue();
        assertThat(accountOf(free.customerId())).isEqualTo(account);
        assertThat(link.linkIfUnlinked(tenant, free.customerId(), UUID.randomUUID())).isFalse();
        assertThat(accountOf(free.customerId())).isEqualTo(account);
        assertThat(link.linkIfUnlinked(tenant, taken.customerId(), UUID.randomUUID())).isFalse();
        assertThat(accountOf(taken.customerId())).isEqualTo(kept);
        assertThat(link.linkIfUnlinked(tenant, foreign.customerId(), UUID.randomUUID())).isFalse();
        assertThat(accountOf(foreign.customerId())).isNull();
        assertThat(link.linkIfUnlinked(tenant, UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }
}
