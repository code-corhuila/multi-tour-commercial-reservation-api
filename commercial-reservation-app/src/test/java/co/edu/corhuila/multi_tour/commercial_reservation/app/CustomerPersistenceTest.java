package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.crypto.AesGcmFieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcServiceRiskReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipherException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SensitiveDataAccessOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReadSensitiveCustomerDataUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterLeadCustomerUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-005 over the real schema (db spec T-07, T-08): every value below is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CustomerPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");
    private static final String DOCUMENT = "DOC-FICTICIO-0001";
    private static final String EMAIL = "persona.uno@example.invalid";
    private static final String PHONE = "+000-555-0100";
    private static final String BLOOD = "ZZ-NEG-FICTICIA";
    private static final String EMERGENCY = "Contacto Ficticio Dos 000-555-0199";
    private static final String RESTRICTION = "Restriccion ficticia de movilidad";
    private static final String REASON = "motivo ficticio de consulta";

    private JdbcTemplate jdbc;
    private AesGcmFieldCipher cipher;
    private JdbcCustomerRepository repository;
    private JdbcCustomerOutbox outbox;
    private JdbcCustomerTransaction transaction;
    private RegisterLeadCustomerUseCase register;
    private final UUID tenant = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();
    private final UUID highRiskService = UUID.randomUUID();
    private final UUID plainService = UUID.randomUUID();

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER")
                && present("TEST_DATABASE_PASSWORD"), "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("commercial_reservation_app");
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        cipher = new AesGcmFieldCipher("test1", Map.of("test1", Base64.getEncoder().encodeToString(key)));
        repository = new JdbcCustomerRepository(jdbc, cipher);
        outbox = new JdbcCustomerOutbox(jdbc);
        transaction = new JdbcCustomerTransaction(new TransactionTemplate(new DataSourceTransactionManager(source)));
        register = new RegisterLeadCustomerUseCase(repository, outbox, transaction, new JdbcServiceRiskReader(jdbc),
                UUID::randomUUID, Clock.fixed(NOW, ZoneOffset.UTC));
        insertService(highRiskService, tenant, true);
        insertService(plainService, tenant, false);
    }

    @Test
    void nothingReadableIsStoredInTheSensitiveColumns() {
        var saved = register.register(actor(tenant), command(highRiskService, true));
        var raw = jdbc.queryForMap("SELECT c.full_name, c.document_number, c.birth_date_or_age, "
                + "c.contact_info::text AS contact_info, s.emergency_info::text AS emergency_info "
                + "FROM commercial_reservation.customer c JOIN commercial_reservation.customer_sensitive_data s "
                + "ON s.customer_id = c.customer_id WHERE c.customer_id = ?", saved.customerId());
        for (String column : new String[] {"document_number", "birth_date_or_age", "contact_info", "emergency_info"}) {
            String stored = String.valueOf(raw.get(column));
            assertThat(stored).as(column).contains("v1:test1:")
                    .doesNotContain(DOCUMENT, "1990-05-17", EMAIL, PHONE, BLOOD, EMERGENCY, RESTRICTION);
        }
        assertThat(String.valueOf(raw.get("document_number"))).startsWith("v1:test1:");
        assertThat(String.valueOf(raw.get("birth_date_or_age"))).startsWith("v1:test1:");
    }

    @Test
    void aSavedCustomerReadsBackDecryptedAndOnlyInItsOwnTenant() {
        var saved = register.register(actor(tenant), command(highRiskService, true));
        var found = repository.findByTenantAndId(tenant, saved.customerId()).orElseThrow();
        assertThat(found.documentNumber().value()).isEqualTo(DOCUMENT);
        assertThat(found.fullName()).isEqualTo("Persona Ficticia Uno");
        assertThat(found.birthDateOrAge().birthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(found.contact().email()).isEqualTo(EMAIL);
        assertThat(found.contact().phone()).isEqualTo(PHONE);
        assertThat(found.createdAt()).isEqualTo(NOW);
        assertThat(found.highRiskData()).isEqualTo(risk());
        assertThat(repository.findByTenantAndId(otherTenant, saved.customerId())).isEmpty();
    }

    @Test
    void theOrdinaryReadNeverLoadsNorDecryptsTheRiskData() {
        var saved = register.register(actor(tenant), command(highRiskService, true));
        jdbc.update("UPDATE commercial_reservation.customer_sensitive_data SET emergency_info = "
                + "'{\"envelope\":\"v1:test1:not-a-real-envelope\"}'::jsonb WHERE customer_id = ?", saved.customerId());
        var found = repository.findOrdinary(tenant, saved.customerId()).orElseThrow();
        assertThat(found.highRiskData()).isNull();
        assertThat(found.documentNumber().value()).isEqualTo(DOCUMENT);
        assertThat(found.contact().email()).isEqualTo(EMAIL);
        assertThat(repository.findOrdinary(otherTenant, saved.customerId())).isEmpty();
        assertThatThrownBy(() -> repository.findByTenantAndId(tenant, saved.customerId()))
                .isInstanceOf(FieldCipherException.class);
    }

    @Test
    void anAgeAndAnOrdinaryActivityStoreNoSensitiveRow() {
        var saved = register.register(actor(tenant), new RegisterLeadCustomer.Command(plainService, DOCUMENT,
                "Persona Ficticia Uno", null, 34, null, PHONE, true, null));
        assertThat(count("customer_sensitive_data", saved.customerId())).isZero();
        var found = repository.findByTenantAndId(tenant, saved.customerId()).orElseThrow();
        assertThat(found.birthDateOrAge().age()).isEqualTo(34);
        assertThat(found.birthDateOrAge().birthDate()).isNull();
        assertThat(found.contact().email()).isNull();
        assertThat(found.highRiskData()).isNull();
    }

    @Test
    void aCustomerIdOfAnotherTenantIsNeverOverwritten() {
        var saved = register.register(actor(tenant), command(highRiskService, true));
        var copy = repository.findByTenantAndId(tenant, saved.customerId()).orElseThrow();
        var clash = co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer.registerLead(
                saved.customerId(), otherTenant, "OTRO-DOC-FICTICIO", "Persona Ficticia Tres", copy.birthDateOrAge(),
                copy.contact(), true, NOW);
        assertThatThrownBy(() -> repository.save(clash)).isInstanceOf(RuntimeException.class);
        assertThat(repository.findByTenantAndId(tenant, saved.customerId()).orElseThrow().fullName())
                .isEqualTo("Persona Ficticia Uno");
    }

    @Test
    void registrationWritesTheEventWithTheCustomerAndNoPersonalData() {
        var actor = actor(tenant);
        var saved = register.register(actor, command(highRiskService, true));
        var row = jdbc.queryForMap("SELECT tenant_id, event_type, actor_id, occurred_at, payload::text AS payload, "
                + "service_id, association_id, discount_rule_id, reservation_id FROM commercial_reservation.outbox_event "
                + "WHERE customer_id = ?", saved.customerId());
        assertThat(row.get("event_type")).isEqualTo("CustomerRegistered");
        assertThat(row.get("tenant_id")).isEqualTo(tenant);
        assertThat(row.get("actor_id")).isEqualTo(actor.actorId());
        assertThat(((java.sql.Timestamp) row.get("occurred_at")).toInstant()).isEqualTo(NOW);
        assertThat(row.get("service_id")).isNull();
        String payload = String.valueOf(row.get("payload"));
        assertThat(payload).contains(saved.customerId().toString())
                .doesNotContain(DOCUMENT, EMAIL, PHONE, BLOOD, "Persona Ficticia");
    }

    @Test
    void aFailingEventRollsTheCustomerBackToo() {
        var failing = new RegisterLeadCustomerUseCase(repository, event -> {
            throw new IllegalStateException("outbox down");
        }, transaction, new JdbcServiceRiskReader(jdbc), UUID::randomUUID, Clock.fixed(NOW, ZoneOffset.UTC));
        int before = countByTenant("customer", tenant);
        assertThatThrownBy(() -> failing.register(actor(tenant), command(highRiskService, true)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(countByTenant("customer", tenant)).isEqualTo(before);
        assertThat(countByTenant("customer_sensitive_data", tenant)).isZero();
    }

    @Test
    void theSensitiveReadAuditsBeforeAnsweringWithoutTheData() {
        var saved = register.register(actor(tenant), command(highRiskService, true));
        var reader = new ReadSensitiveCustomerDataUseCase(repository, outbox, transaction, Clock.fixed(NOW, ZoneOffset.UTC));
        var reading = actor(tenant, Set.of("CONSULT_SENSITIVE_DATA"));
        assertThat(reader.read(reading, saved.customerId(), REASON)).contains(risk());
        var event = jdbc.queryForMap("SELECT event_type, actor_id, payload::text AS payload "
                + "FROM commercial_reservation.outbox_event WHERE customer_id = ? AND event_type = 'SensitiveDataAccessed'",
                saved.customerId());
        assertThat(event.get("actor_id")).isEqualTo(reading.actorId());
        assertThat(String.valueOf(event.get("payload"))).contains(REASON).doesNotContain(BLOOD, EMERGENCY, RESTRICTION, DOCUMENT);
    }

    @Test
    void ifTheAuditWriteFailsNothingIsReturnedAndNoAuditRowSurvives() {
        var saved = register.register(actor(tenant), command(highRiskService, true));
        SensitiveDataAccessOutbox failsAfterWriting = event -> {
            outbox.append(event);
            throw new IllegalStateException("audit down");
        };
        var reader = new ReadSensitiveCustomerDataUseCase(repository, failsAfterWriting, transaction,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> reader.read(actor(tenant, Set.of("CONSULT_SENSITIVE_DATA")), saved.customerId(), null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event "
                + "WHERE customer_id = ? AND event_type = 'SensitiveDataAccessed'", Integer.class, saved.customerId()))
                .isZero();
    }

    @Test
    void anEnvelopeMovedToAnotherCustomerDoesNotDecrypt() {
        var first = register.register(actor(tenant), command(highRiskService, true));
        var second = register.register(actor(tenant), command(highRiskService, true));
        jdbc.update("UPDATE commercial_reservation.customer SET document_number = "
                + "(SELECT document_number FROM commercial_reservation.customer WHERE customer_id = ?) "
                + "WHERE customer_id = ?", first.customerId(), second.customerId());
        assertThatThrownBy(() -> repository.findByTenantAndId(tenant, second.customerId()))
                .isInstanceOf(FieldCipherException.class);
    }

    @Test
    void theRiskMarkComesFromTheCatalogOfTheSameTenantOnly() {
        var reader = new JdbcServiceRiskReader(jdbc);
        assertThat(reader.isHighRisk(tenant, highRiskService)).contains(true);
        assertThat(reader.isHighRisk(tenant, plainService)).contains(false);
        assertThat(reader.isHighRisk(otherTenant, highRiskService)).isEmpty();
    }

    private RegisterLeadCustomer.Command command(UUID service, boolean withRisk) {
        return new RegisterLeadCustomer.Command(service, DOCUMENT, "Persona Ficticia Uno", LocalDate.of(1990, 5, 17),
                null, EMAIL, PHONE, true, withRisk ? risk() : null);
    }

    private static HighRiskData risk() {
        return new HighRiskData(BLOOD, EMERGENCY, RESTRICTION, true);
    }

    private static CustomerActor actor(UUID tenantId) {
        return actor(tenantId, Set.of());
    }

    private static CustomerActor actor(UUID tenantId, Set<String> permissions) {
        return new CustomerActor(tenantId, UUID.randomUUID(), CustomerActor.Role.OPERATIONAL_COLLABORATOR, permissions);
    }

    private void insertService(UUID serviceId, UUID tenantId, boolean highRisk) {
        jdbc.update("INSERT INTO commercial_reservation.catalog_service (service_id, tenant_id, service_type, "
                + "commercial_name, catalog_status, base_rate_cents, base_operational_cost_cents, configured_capacity, "
                + "transport_required, high_risk, created_at, updated_at) VALUES (?, ?, 'TOUR', 'Servicio Ficticio', "
                + "'ACTIVE', 100, 50, 10, false, ?, ?, ?)", serviceId, tenantId, highRisk,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    private int count(String table, UUID customerId) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation." + table + " WHERE customer_id = ?",
                Integer.class, customerId);
    }

    private int countByTenant(String table, UUID tenantId) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation." + table + " WHERE tenant_id = ?",
                Integer.class, tenantId);
    }

    private static boolean present(String variable) {
        String value = System.getenv(variable);
        return value != null && !value.isBlank();
    }
}
