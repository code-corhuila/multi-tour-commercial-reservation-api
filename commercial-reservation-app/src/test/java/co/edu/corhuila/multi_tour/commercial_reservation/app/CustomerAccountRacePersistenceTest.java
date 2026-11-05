package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.crypto.AesGcmFieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// HU-COM-008: two first reservations of one account without a record register the holder at once. The unique key of the
// account (uq_customer_tenant_id_account_id) lets one win; the other is a business refusal, never an unexpected failure.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CustomerAccountRacePersistenceTest {
    private final UUID tenant = UUID.randomUUID();
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

    private Customer customerOf() {
        UUID id = UUID.randomUUID();
        return Customer.registerLead(id, tenant, "DOC-" + id, "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("carrera." + id + "@example.invalid", "000-555-0100"), true, Instant.parse("2026-11-04T12:00:00Z"));
    }

    @Test void twoRegistrationsOfOneAccountAtOnceStoreOneRecordAndRefuseTheOtherAsABusinessRule() throws Exception {
        UUID account = UUID.randomUUID();
        List<Customer> contenders = List.of(customerOf().linkedTo(account), customerOf().linkedTo(account));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Throwable>> outcomes = new ArrayList<>();
        for (Customer contender : contenders) {
            Callable<Throwable> attempt = () -> {
                start.await();
                try { repository.save(contender); return null; } catch (RuntimeException failure) { return failure; }
            };
            outcomes.add(pool.submit(attempt));
        }
        start.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> outcome : outcomes) { Throwable failure = outcome.get(); if (failure != null) failures.add(failure); }
        pool.shutdown();

        assertThat(failures).singleElement().isInstanceOf(BusinessRuleViolation.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.customer WHERE tenant_id = ? AND account_id = ?",
                Integer.class, tenant, account)).isEqualTo(1);
    }
}
