package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.crypto.AesGcmFieldCipher;
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

// The e-mail lookup of the inline holder and the account link over the real schema (HU-COM-005 AC-05.7, AC-05.8; HU-COM-008
// AC-08.13). The e-mail is stored sealed, so the lookup compares the opened value; every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CustomerEmailPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");
    private final UUID tenant = UUID.randomUUID(), otherTenant = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private JdbcCustomerRepository repository;

    @BeforeEach void connect() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null
                && System.getenv("TEST_DATABASE_PASSWORD") != null, "All three TEST_DATABASE variables are required");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD")));
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        repository = new JdbcCustomerRepository(jdbc, new AesGcmFieldCipher("test1", Map.of("test1", Base64.getEncoder().encodeToString(key))));
    }

    private Customer customer(UUID id, UUID owner, String email) {
        return Customer.registerLead(id, owner, "DOC-" + id, "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo(email, "000-555-0100"), true, NOW);
    }

    @Test void aRecordWithoutAnAccountIsAnUnverifiedMatchAndOneWithAnAccountIsVerified() {
        var plain = UUID.randomUUID();
        var linked = UUID.randomUUID();
        repository.save(customer(plain, tenant, "sin.cuenta@example.invalid"));
        repository.save(customer(linked, tenant, "con.cuenta@example.invalid").linkedTo(UUID.randomUUID()));

        assertThat(repository.findByTenantAndEmail(tenant, "sin.cuenta@example.invalid")).hasValueSatisfying(match -> {
            assertThat(match.customerId()).isEqualTo(plain);
            assertThat(match.verified()).isFalse();
        });
        assertThat(repository.findByTenantAndEmail(tenant, "con.cuenta@example.invalid")).hasValueSatisfying(match -> {
            assertThat(match.customerId()).isEqualTo(linked);
            assertThat(match.verified()).isTrue();
        });
    }

    @Test void theLookupNeverCrossesTheTenantAndAnUnknownEmailMatchesNothing() {
        repository.save(customer(UUID.randomUUID(), otherTenant, "ajena@example.invalid"));

        assertThat(repository.findByTenantAndEmail(tenant, "ajena@example.invalid")).isEmpty();
        assertThat(repository.findByTenantAndEmail(tenant, "nadie@example.invalid")).isEmpty();
    }

    @Test void theAccountLinkIsStoredWithTheRecordAndFoundByTheAccount() {
        var id = UUID.randomUUID();
        var account = UUID.randomUUID();
        repository.save(customer(id, tenant, "enlazada@example.invalid").linkedTo(account));

        assertThat(jdbc.queryForObject("SELECT account_id FROM commercial_reservation.customer WHERE customer_id = ?", UUID.class, id))
                .isEqualTo(account);
        assertThat(repository.findByTenantAndAccount(tenant, account)).hasValueSatisfying(found -> assertThat(found.customerId()).isEqualTo(id));
    }
}
