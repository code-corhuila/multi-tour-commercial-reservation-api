package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerAuthorization;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-005 over HTTP against the real schema: idempotency, tenant isolation, the audit row and the split read.
// Every value is invented; the database rows are checked as raw columns.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = CatalogHttpPersistenceTest.Database.class)
class CustomerHttpPersistenceTest {
    static final String BASE = CustomerHttpTest.BASE;
    static final String ADMIN = "TENANT_ADMINISTRATOR";
    static final String BLOOD = "ZZ-NEG-FICTICIA", EMERGENCY = "Contacto Ficticio 000-555-0199",
            RESTRICTION = "Restriccion ficticia";

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock customerClock() { return Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC); }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void aRegistrationIsStoredOnceTheRepeatedKeyReturnsIt200AndTheKeyIsOfTypeCustomer() throws Exception {
        UUID service = insertService(true);
        String key = "customer-" + UUID.randomUUID();
        var created = CatalogHttpTest.json(exchange(registration(body(service), key, TestJwtKeys.TENANT), 201));
        var again = CatalogHttpTest.json(exchange(registration(body(service), key, TestJwtKeys.TENANT), 200));
        assertThat(again).isEqualTo(((com.fasterxml.jackson.databind.node.ObjectNode) created.deepCopy()).without("highRisk"));
        UUID id = UUID.fromString(created.get("customerId").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.customer WHERE customer_id = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT resource_type FROM commercial_reservation.idempotency_key "
                + "WHERE tenant_id = ? AND key = ?", String.class, UUID.fromString(TestJwtKeys.TENANT), key))
                .isEqualTo("customer");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event "
                + "WHERE customer_id = ? AND event_type = 'CustomerRegistered'", Integer.class, id)).isEqualTo(1);
    }

    @Test void theGeneralReadHidesTheRiskDataAndTheSensitiveReadAuditsBeforeAnswering() throws Exception {
        UUID service = insertService(true);
        var created = CatalogHttpTest.json(exchange(registration(body(service), "customer-" + UUID.randomUUID(),
                TestJwtKeys.TENANT), 201));
        String id = created.get("customerId").asText();
        var general = exchange(get(BASE + "/" + id).header("Authorization", "Bearer "
                + CustomerHttpTest.token(TestJwtKeys.TENANT, "MANAGER", List.of())), 200);
        assertThat(general.getResponse().getContentAsString()).doesNotContain(BLOOD, EMERGENCY, RESTRICTION);
        assertThat(CatalogHttpTest.json(general).get("documentNumber").asText()).isEqualTo("AB-0000");
        assertThat(auditRows(id)).isZero();

        var sensitive = exchange(get(BASE + "/" + id + "/sensitive-data").param("reason", "motivo ficticio")
                .header("Authorization", "Bearer " + CustomerHttpTest.token(TestJwtKeys.TENANT, ADMIN,
                        List.of(CustomerAuthorization.SENSITIVE_DATA_PERMISSION))), 200);
        assertThat(sensitive.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(CatalogHttpTest.json(sensitive).at("/emergencyInfo/bloodType").asText()).isEqualTo(BLOOD);
        assertThat(auditRows(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload::text FROM commercial_reservation.outbox_event "
                + "WHERE customer_id = ? AND event_type = 'SensitiveDataAccessed'", String.class, UUID.fromString(id)))
                .contains("motivo ficticio").doesNotContain(BLOOD, EMERGENCY, RESTRICTION);
    }

    @Test void aCustomerOfAnotherTenantAnswers404OnBothReadsAndTheDeniedReadLeavesNoAudit() throws Exception {
        UUID service = insertService(false);
        var created = CatalogHttpTest.json(exchange(registration(simple(service), "customer-" + UUID.randomUUID(),
                TestJwtKeys.TENANT), 201));
        String id = created.get("customerId").asText();
        String other = UUID.randomUUID().toString();
        exchange(get(BASE + "/" + id).header("Authorization", "Bearer " + CustomerHttpTest.token(other, ADMIN, List.of())), 404);
        exchange(get(BASE + "/" + id + "/sensitive-data").header("Authorization", "Bearer " + CustomerHttpTest.token(other,
                ADMIN, List.of(CustomerAuthorization.SENSITIVE_DATA_PERMISSION))), 404);
        exchange(get(BASE + "/" + id + "/sensitive-data").header("Authorization", "Bearer " + CustomerHttpTest.token(
                TestJwtKeys.TENANT, ADMIN, List.of())), 403);
        assertThat(auditRows(id)).isZero();
    }

    private Map<String, Object> simple(UUID service) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Persona Ficticia Uno"); body.put("documentNumber", " ab-0000 ");
        body.put("birthDate", "1990-05-17");
        body.put("contact", Map.of("email", "Persona.Uno@Example.invalid", "phone", "3000000000"));
        body.put("termsAccepted", true); body.put("serviceId", service.toString());
        return body;
    }

    private Map<String, Object> body(UUID service) {
        var body = simple(service);
        body.put("informedConsentSigned", true);
        body.put("emergencyInfo", Map.of("bloodType", BLOOD, "emergencyContact", EMERGENCY,
                "mobilityRestrictions", RESTRICTION));
        return body;
    }

    private MockHttpServletRequestBuilder registration(Map<String, Object> request, String key, String tenant)
            throws Exception {
        return post(BASE).header("Authorization", "Bearer " + CustomerHttpTest.token(tenant, ADMIN, List.of()))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(request));
    }

    private int auditRows(String customerId) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event "
                + "WHERE customer_id = ? AND event_type = 'SensitiveDataAccessed'", Integer.class, UUID.fromString(customerId));
    }

    private UUID insertService(boolean highRisk) {
        UUID service = UUID.randomUUID();
        var now = OffsetDateTime.ofInstant(TestJwtKeys.NOW, ZoneOffset.UTC);
        jdbc.update("INSERT INTO commercial_reservation.catalog_service (service_id, tenant_id, service_type, "
                + "commercial_name, catalog_status, base_rate_cents, base_operational_cost_cents, configured_capacity, "
                + "transport_required, high_risk, created_at, updated_at) VALUES (?, ?, 'TOUR', 'Servicio Ficticio', "
                + "'ACTIVE', 100, 50, 10, false, ?, ?, ?)", service, UUID.fromString(TestJwtKeys.TENANT), highRisk, now, now);
        return service;
    }

    private MvcResult exchange(MockHttpServletRequestBuilder request, int code) throws Exception {
        MvcResult result = mvc.perform(request.header("X-Correlation-Id", CustomerHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }
}
