package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-013 DP-02 over HTTP with every real JDBC bean and no fake (docs pull request 1002): the Administrator writes the penalty policy, the
// cancellation of a reservation with validated values reads it (50 % over 300.000 gives a penalty of 150.000 and a refundable amount of
// 150.000), and with no policy the behaviour is the old one. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
@Import(CancellationPolicyJdbcHttpTest.FixedClock.class)
class CancellationPolicyJdbcHttpTest {
    static final String BASE = "/api/v1/commercial-reservation";
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock cancellationPolicyJdbcClock() { return Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC); }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogServiceRepository catalog;
    @Autowired CustomerRepository customers;

    final UUID tenant = UUID.randomUUID(), tour = UUID.randomUUID(), departure = UUID.randomUUID(), customer = UUID.randomUUID();

    @BeforeEach void seed() throws Exception {
        OffsetDateTime now = OffsetDateTime.ofInstant(TestJwtKeys.NOW, ZoneOffset.UTC);
        jdbc.update("INSERT INTO commercial_reservation.tenant_status (tenant_id, identifier, status, time_zone, country, "
                + "last_event_occurred_at, created_at, updated_at) VALUES (?, ?, 'ACTIVE', 'America/Bogota', 'CO', ?, ?, ?)",
                tenant, "t-" + tenant, now, now, now);
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio",
                CommercialTerms.of(100_000L, 40_000L, 20, false, false), TestJwtKeys.NOW));
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, "
                + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 4)",
                UUID.randomUUID(), tenant, departure, tour, now.plusDays(1));
        customers.save(Customer.registerLead(customer, tenant, "CC12345", "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("persona@example.invalid", "3000000000"), true, TestJwtKeys.NOW));
        var row = Map.of("paymentType", "DEPOSIT", "initialWaitHours", 24, "additionalWaitHours", 48, "minimumDepositBp", 3000,
                "transferEnabled", true, "cashEnabled", true);
        exchange(put(BASE + "/payment-parameters").header("Idempotency-Key", "param-pen-0001").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("reason", "Opening season", "parameters", List.of(row)))), token("TENANT_ADMINISTRATOR"), 200);
    }

    String token(String role) throws Exception { return CatalogHttpTest.token(tenant.toString(), role); }

    MvcResult exchange(MockHttpServletRequestBuilder request, String bearer, int code) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    MockHttpServletRequestBuilder policy(Object body, String key) throws Exception {
        var request = put(BASE + "/cancellation-policies").contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    Map<String, Object> policyBody(UUID service, Object basisPoints, String reason) {
        var row = new java.util.HashMap<String, Object>();
        if (service != null) row.put("serviceId", service.toString());
        row.put("penaltyBasisPoints", basisPoints);
        return reason == null ? Map.of("policies", List.of(row)) : Map.of("reason", reason, "policies", List.of(row));
    }

    // A reservation of 300.000 with `validated` approved by the Administrator; `inValidation` is left waiting for review.
    UUID reservationWithValidated(String key, long validated, long inValidation) throws Exception {
        var body = Map.of("serviceId", tour.toString(), "salidaId", departure.toString(), "people", 3, "customerId", customer.toString(),
                "paymentType", "DEPOSIT", "paymentMethod", "TRANSFER");
        String id = CatalogHttpTest.json(exchange(post(BASE + "/reservations").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body)), token("TENANT_ADMINISTRATOR"), 201)).get("reservationId").asText();
        String proof = transfer(id, validated, key + "-p1");
        // The only way to approve (DP-11): step 2 of the saga records the validated payment for the person who decided, under the service token.
        exchange(post(BASE + "/internal/tenants/" + tenant + "/payment-proofs/" + proof + "/validated-payment").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("approvedBy", TestJwtKeys.ACCOUNT, "verifiedAmountCents", validated))),
                CustomerHttpTest.token(null, "SERVICE", List.of()), 200);
        if (inValidation > 0) transfer(id, inValidation, key + "-p2");
        return UUID.fromString(id);
    }

    String transfer(String reservation, long amount, String key) throws Exception {
        return CatalogHttpTest.json(exchange(multipart(BASE + "/reservations/" + reservation + "/payments")
                .file(new MockMultipartFile("payment", "", "application/json",
                        TestJwtKeys.JSON.writeValueAsBytes(Map.of("paymentMethod", "TRANSFER", "amountCents", amount))))
                .file(new MockMultipartFile("proof", "proof.png", "image/png", PNG)).header("Idempotency-Key", key), token("TENANT_ADMINISTRATOR"), 201))
                .get("paymentId").asText();
    }

    JsonNode cancel(UUID reservation, String key) throws Exception {
        return CatalogHttpTest.json(exchange(post(BASE + "/reservations/" + reservation + "/cancellation").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(
                        Map.of("reason", "The customer cannot travel", "origin", "CUSTOMER_REQUEST"))), token("OPERATIONAL_COLLABORATOR"), 201));
    }

    // AC-13.3 over the real service: the example of the PDR, 50 % over 300.000, with the policy the Administrator wrote.
    @Test void aFiftyPercentPolicyOver300000ValidatedGivesAPenaltyOf150000AndARefundableOf150000() throws Exception {
        UUID id = reservationWithValidated("pen-http-0001", 300_000, 0);
        exchange(policy(policyBody(tour, 5_000, "Seasonal commercial policy"), "pen-http-0002"), token("TENANT_ADMINISTRATOR"), 200);

        JsonNode quote = CatalogHttpTest.json(exchange(get(BASE + "/reservations/" + id + "/cancellation-quote"), token("OPERATIONAL_COLLABORATOR"), 200));
        JsonNode cancelled = cancel(id, "pen-http-0003");

        assertThat(quote.get("penaltyBasisPoints").asInt()).isEqualTo(5_000);
        assertThat(quote.get("penaltyCents").asLong()).isEqualTo(150_000L);
        assertThat(quote.get("refundableCents").asLong()).isEqualTo(150_000L);
        assertThat(cancelled.get("validatedValueCents").asLong()).isEqualTo(300_000L);
        assertThat(cancelled.get("penaltyBasisPoints").asInt()).isEqualTo(5_000);
        assertThat(cancelled.get("penaltyCents").asLong()).isEqualTo(150_000L);
        assertThat(cancelled.get("refundableCents").asLong()).isEqualTo(150_000L);
    }

    @Test void withNoPolicyTheCancellationKeepsTheWholeValidatedAmountAsItDidBefore() throws Exception {
        UUID id = reservationWithValidated("pen-http-0010", 300_000, 0);

        JsonNode cancelled = cancel(id, "pen-http-0011");

        assertThat(cancelled.get("penaltyBasisPoints").asInt()).isZero();
        assertThat(cancelled.get("penaltyCents").asLong()).isZero();
        assertThat(cancelled.get("refundableCents").asLong()).isEqualTo(300_000L);
    }

    @Test void aProofInValidationDoesNotCountAndTheTenantRowAppliesWhenTheServiceHasNone() throws Exception {
        UUID id = reservationWithValidated("pen-http-0020", 100_000, 50_000);
        exchange(policy(policyBody(null, 5_000, "Tenant level"), "pen-http-0021"), token("TENANT_ADMINISTRATOR"), 200);

        JsonNode cancelled = cancel(id, "pen-http-0022");

        assertThat(cancelled.get("validatedValueCents").asLong()).isEqualTo(100_000L);
        assertThat(cancelled.get("penaltyCents").asLong()).isEqualTo(50_000L);
        assertThat(cancelled.get("refundableCents").asLong()).isEqualTo(50_000L);
    }

    @Test void theAdministratorWritesTheStaffReadsAndEveryoneElseIsRefusedOnTheServer() throws Exception {
        for (String role : List.of("OPERATIONAL_COLLABORATOR", "MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER")) {
            exchange(policy(policyBody(tour, 5_000, "Season"), "pen-http-" + role.length() + role.charAt(0)), token(role), 403);
        }
        exchange(policy(policyBody(tour, 5_000, "Season"), "pen-http-0030"), token("TENANT_ADMINISTRATOR"), 200);

        JsonNode rows = CatalogHttpTest.json(exchange(get(BASE + "/cancellation-policies"), token("OPERATIONAL_COLLABORATOR"), 200));
        exchange(get(BASE + "/cancellation-policies"), token("FINAL_CUSTOMER"), 403);

        assertThat(rows.at("/meta/total").asInt()).isEqualTo(1);
        assertThat(rows.at("/data/0/serviceId").asText()).isEqualTo(tour.toString());
        assertThat(rows.at("/data/0/penaltyBasisPoints").asInt()).isEqualTo(5_000);
        assertThat(rows.at("/data/0/changeReason").asText()).isEqualTo("Season");
    }

    @Test void aMissingReasonAValueOutOfBoundsAMissingKeyOrAForeignServiceIsRefused() throws Exception {
        exchange(policy(policyBody(tour, 5_000, null), "pen-http-0040"), token("TENANT_ADMINISTRATOR"), 400);
        exchange(policy(policyBody(tour, 10_001, "Season"), "pen-http-0041"), token("TENANT_ADMINISTRATOR"), 400);
        exchange(policy(policyBody(tour, null, "Season"), "pen-http-0042"), token("TENANT_ADMINISTRATOR"), 400);
        exchange(policy(policyBody(tour, 5_000, "Season"), null), token("TENANT_ADMINISTRATOR"), 400);
        exchange(policy(policyBody(UUID.randomUUID(), 5_000, "Season"), "pen-http-0043"), token("TENANT_ADMINISTRATOR"), 404);
        exchange(policy(Map.of("reason", "Season", "policies", List.of(Map.of("penaltyBasisPoints", 5_000, "tenantId", tenant.toString()))), "pen-http-0044"),
                token("TENANT_ADMINISTRATOR"), 400);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.cancellation_penalty_policy WHERE tenant_id = ?", Integer.class, tenant))
                .isZero();
    }

    @Test void aRepeatedKeyWithTheSameBodyAnswersAndAnotherBodyIsARuleViolation() throws Exception {
        exchange(policy(policyBody(tour, 5_000, "Season"), "pen-http-0050"), token("TENANT_ADMINISTRATOR"), 200);

        exchange(policy(policyBody(tour, 5_000, "Season"), "pen-http-0050"), token("TENANT_ADMINISTRATOR"), 200);
        exchange(policy(policyBody(tour, 3_000, "Season"), "pen-http-0050"), token("TENANT_ADMINISTRATOR"), 422);

        assertThat(jdbc.queryForObject("SELECT penalty_bp FROM commercial_reservation.cancellation_penalty_policy WHERE tenant_id = ?", Integer.class, tenant))
                .isEqualTo(5_000);
    }
}
