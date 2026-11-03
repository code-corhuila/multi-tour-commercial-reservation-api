package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcEnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-007 plan D-05 and RN-14, RN-16 on the real schema: the enabled payment options read from payment_condition and the
// tenant lookup of the creation check read from tenant_status by tenant id. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CreatePortsPersistenceTest {
    private final UUID tenant = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private JdbcEnabledPaymentOptions options;
    private JdbcTenantStatusDirectory tenants;
    private CatalogService tour;

    @BeforeEach
    void connectAsTheApplicationUser() {
        assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"), System.getenv("TEST_DATABASE_USER"),
                System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        options = new JdbcEnabledPaymentOptions(jdbc);
        tenants = new JdbcTenantStatusDirectory(jdbc);
        tour = CatalogService.register(UUID.randomUUID(), tenant, CatalogServiceType.TOUR, "Canyon",
                CommercialTerms.of(10_000_000L, 4_000_000L, 12, false, false), Instant.now());
        new JdbcCatalogServiceRepository(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source))).save(tour);
    }

    @Test
    void withNoConditionConfiguredNoTypeAndNoMediumIsEnabled() { // DP-11: a row enables a type; no row offers nothing
        for (PaymentType type : PaymentType.values()) {
            for (PaymentMethod method : PaymentMethod.values()) {
                assertThat(options.isEnabled(tenant, tour.serviceId(), type, method)).isFalse();
            }
        }
    }

    @Test
    void theTenantConditionDisablesTheMediumOnlyForItsPaymentType() {
        condition(null, "DEPOSIT", false, true);

        assertThat(options.isEnabled(tenant, tour.serviceId(), PaymentType.DEPOSIT, PaymentMethod.TRANSFER)).isFalse();
        assertThat(options.isEnabled(tenant, tour.serviceId(), PaymentType.DEPOSIT, PaymentMethod.CASH)).isTrue();
        assertThat(options.isEnabled(tenant, tour.serviceId(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER)).isFalse(); // no row
    }

    @Test
    void theConditionOfTheServiceWinsOverTheConditionOfTheTenant() {
        condition(null, "FULL_PAYMENT", true, true);
        condition(tour.serviceId(), "FULL_PAYMENT", false, false);

        assertThat(options.isEnabled(tenant, tour.serviceId(), PaymentType.FULL_PAYMENT, PaymentMethod.CASH)).isFalse();
        assertThat(options.isEnabled(tenant, UUID.randomUUID(), PaymentType.FULL_PAYMENT, PaymentMethod.CASH)).isTrue();
    }

    @Test
    void theConditionOfAnotherTenantIsNeverRead() {
        UUID stranger = UUID.randomUUID();
        jdbc.update("INSERT INTO commercial_reservation.payment_condition (payment_condition_id, tenant_id, service_id, "
                + "payment_type, initial_wait_hours, additional_wait_hours, minimum_deposit_bp, balance_deadline_advance_hours, "
                + "transfer_enabled, cash_enabled, changed_by, change_reason, changed_at) VALUES (?, ?, null, 'DEPOSIT', 24, 12, "
                + "3000, 48, false, false, ?, 'test', now())", UUID.randomUUID(), stranger, UUID.randomUUID());

        assertThat(options.isEnabled(tenant, tour.serviceId(), PaymentType.DEPOSIT, PaymentMethod.TRANSFER)).isFalse();
    }

    @Test
    void theTenantIsResolvedByItsIdAsWellAsByItsSlugAndKeepsItsStatus() {
        UUID inactive = UUID.randomUUID();
        status(tenant, "ACTIVE");
        status(inactive, "INACTIVE");

        assertThat(tenants.resolve(tenant.toString())).contains(new TenantRef(tenant, true));
        assertThat(tenants.resolve(inactive.toString())).contains(new TenantRef(inactive, false));
        assertThat(tenants.resolve("t-" + tenant)).contains(new TenantRef(tenant, true));
    }

    @Test
    void anUnknownTenantAndAnIdentifierThatIsNotAnIdAreNotFound() {
        assertThat(tenants.resolve(UUID.randomUUID().toString())).isEmpty();
        assertThat(tenants.resolve("not-a-uuid")).isEmpty();
        assertThat(tenants.resolve(null)).isEmpty();
    }

    private void condition(UUID service, String type, boolean transfer, boolean cash) {
        jdbc.update("INSERT INTO commercial_reservation.payment_condition (payment_condition_id, tenant_id, service_id, "
                + "payment_type, initial_wait_hours, additional_wait_hours, minimum_deposit_bp, balance_deadline_advance_hours, "
                + "transfer_enabled, cash_enabled, changed_by, change_reason, changed_at) VALUES (?, ?, ?, ?, 24, 12, ?, 48, ?, ?, "
                + "?, 'test', now())", UUID.randomUUID(), tenant, service, type, type.equals("DEPOSIT") ? 3000 : null, transfer, cash,
                UUID.randomUUID());
    }

    private void status(UUID id, String status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO commercial_reservation.tenant_status (tenant_id, identifier, status, time_zone, country, "
                + "last_event_occurred_at, created_at, updated_at) VALUES (?, ?, ?, 'America/Bogota', 'CO', ?, ?, ?)", id,
                "t-" + id, status, now, now, now);
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
