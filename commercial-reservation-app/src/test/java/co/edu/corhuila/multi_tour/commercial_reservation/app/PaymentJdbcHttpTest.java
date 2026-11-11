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

// HU-COM-009 and HU-COM-010 over HTTP with every real JDBC bean and no fake: one test per route family answers on the happy path with
// rows seeded in tenant_status, catalog_service, salida_capacity and customer, so a port with no adapter shows up here as a 500
// instead of in production. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
@Import(PaymentJdbcHttpTest.FixedClock.class)
class PaymentJdbcHttpTest {
    static final String BASE = "/api/v1/commercial-reservation";
    static final String ADMIN = "TENANT_ADMINISTRATOR";
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock paymentJdbcClock() { return Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC); }
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
        configure(); // DP-11: a tenant with no payment condition offers no type, so the reservation of every test needs the row
    }

    MvcResult exchange(MockHttpServletRequestBuilder request, String bearer, int code) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    String admin() throws Exception { return CatalogHttpTest.token(tenant.toString(), ADMIN); }

    String reservation(String key) throws Exception {
        var body = Map.of("serviceId", tour.toString(), "salidaId", departure.toString(), "people", 3, "customerId", customer.toString(),
                "paymentType", "DEPOSIT", "paymentMethod", "TRANSFER");
        return CatalogHttpTest.json(exchange(post(BASE + "/reservations").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body)), admin(), 201)).get("reservationId").asText();
    }

    void configure() throws Exception {
        var row = Map.of("paymentType", "DEPOSIT", "initialWaitHours", 24, "additionalWaitHours", 48, "minimumDepositBp", 3000,
                "transferEnabled", true, "cashEnabled", true);
        exchange(put(BASE + "/payment-parameters").header("Idempotency-Key", "param-jdbc-0001").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("reason", "Opening season", "parameters", List.of(row)))), admin(), 200);
    }

    JsonNode transfer(String reservation, long amount, String key) throws Exception {
        return transfer(reservation, amount, key, 201);
    }

    JsonNode transfer(String reservation, long amount, String key, int code) throws Exception {
        return CatalogHttpTest.json(exchange(multipart(BASE + "/reservations/" + reservation + "/payments")
                .file(new MockMultipartFile("payment", "", "application/json",
                        TestJwtKeys.JSON.writeValueAsBytes(Map.of("paymentMethod", "TRANSFER", "amountCents", amount))))
                .file(new MockMultipartFile("proof", "proof.png", "image/png", PNG)).header("Idempotency-Key", key), admin(), code));
    }

    @Test void theParametersAndTheQuoteAnswerFromTheDatabase() throws Exception {
        String id = reservation("pay-jdbc-0001");
        configure();

        JsonNode rows = CatalogHttpTest.json(exchange(get(BASE + "/payment-parameters"), admin(), 200));
        JsonNode quote = CatalogHttpTest.json(exchange(get(BASE + "/reservations/" + id + "/payment-quote"), admin(), 200));

        assertThat(rows.at("/meta/total").asInt()).isEqualTo(1);
        assertThat(rows.at("/data/0/changeReason").asText()).isEqualTo("Opening season");
        assertThat(quote.get("paymentType").asText()).isEqualTo("DEPOSIT");
        assertThat(quote.get("minimumDepositCents").asLong()).isEqualTo(90_000L);
        assertThat(quote.get("amountToPayCents").asLong()).isEqualTo(90_000L);
        assertThat(quote.get("pendingBalanceCents").asLong()).isEqualTo(300_000L);
    }

    @Test void aTransferIsStoredWithItsProofAndTheSeatAndIsReadBackByTheHistoryAndTheReview() throws Exception {
        String id = reservation("pay-jdbc-0002");
        configure();

        JsonNode payment = transfer(id, 90_000, "pay-jdbc-0003");
        String paymentId = payment.get("paymentId").asText();

        assertThat(payment.get("validationStatus").asText()).isEqualTo("IN_VALIDATION");
        assertThat(payment.get("seatHeld").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT held_seats FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?",
                Integer.class, tenant, departure)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.payment_proof_file WHERE tenant_id = ?", Integer.class, tenant)).isEqualTo(1);
        assertThat(transfer(id, 90_000, "pay-jdbc-0003", 200).get("paymentId").asText()).isEqualTo(paymentId);
        JsonNode history = CatalogHttpTest.json(exchange(get(BASE + "/reservations/" + id + "/payments"), admin(), 200));
        assertThat(history.at("/meta/total").asInt()).isEqualTo(1);
        exchange(get(BASE + "/reservations/" + id + "/payments/" + paymentId), admin(), 200);

        JsonNode list = CatalogHttpTest.json(exchange(get(BASE + "/payment-proofs"), admin(), 200));
        assertThat(list.at("/meta/total").asInt()).isEqualTo(1);
        assertThat(list.at("/data/0/customerName").asText()).isEqualTo("Persona Ficticia");
        exchange(get(BASE + "/payment-proofs/" + paymentId), admin(), 200);
        MvcResult file = exchange(get(BASE + "/payment-proofs/" + paymentId + "/file"), admin(), 200);
        assertThat(file.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(file.getResponse().getHeader("Cache-Control")).contains("no-store");
    }

    @Test void theApprovalConfirmsTheReservationAndTheRejectionKeepsTheHistory() throws Exception {
        String id = reservation("pay-jdbc-0004");
        configure();
        String first = transfer(id, 90_000, "pay-jdbc-0005").get("paymentId").asText();
        String second = transfer(id, 20_000, "pay-jdbc-0006").get("paymentId").asText();

        JsonNode approved = CatalogHttpTest.json(exchange(post(BASE + "/payment-proofs/" + first + "/approve").header("Idempotency-Key", "pay-jdbc-0007")
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("verifiedAmountCents", 90_000L))), admin(), 200));
        JsonNode rejected = CatalogHttpTest.json(exchange(post(BASE + "/payment-proofs/" + second + "/reject").header("Idempotency-Key", "pay-jdbc-0008")
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("reason", "Illegible proof"))), admin(), 200));

        assertThat(approved.get("reservationStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(approved.get("validatedValueCents").asLong()).isEqualTo(90_000L);
        assertThat(rejected.get("validationStatus").asText()).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT reservation_status FROM commercial_reservation.reservation WHERE reservation_id = ?",
                String.class, UUID.fromString(id))).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForList("SELECT event_type FROM commercial_reservation.outbox_event WHERE reservation_id = ?", String.class, UUID.fromString(id)))
                .contains("PaymentEvidenceRegistered", "CapacityAllocated", "PaymentValidated", "ReservationConfirmed", "PaymentRejected");
        exchange(post(BASE + "/payment-proofs/" + first + "/approve").header("Idempotency-Key", "pay-jdbc-0009")
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("verifiedAmountCents", 90_000L))), admin(), 422);
    }

    @Test void step2OfTheSagaAndTheOverdueListAnswerToAServiceTokenAndCashIsRefusedWithoutAVerifiableSession() throws Exception {
        String id = reservation("pay-jdbc-0010");
        configure();
        String proof = transfer(id, 90_000, "pay-jdbc-0011").get("paymentId").asText();
        String service = CustomerHttpTest.token(null, "SERVICE", List.of());
        var body = Map.of("approvedBy", UUID.randomUUID().toString(), "verifiedAmountCents", 90_000L);

        JsonNode recorded = CatalogHttpTest.json(exchange(post(BASE + "/internal/tenants/" + tenant + "/payment-proofs/" + proof + "/validated-payment")
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body)), service, 200));
        JsonNode again = CatalogHttpTest.json(exchange(post(BASE + "/internal/tenants/" + tenant + "/payment-proofs/" + proof + "/validated-payment")
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body)), service, 200));
        JsonNode overdue = CatalogHttpTest.json(exchange(get(BASE + "/internal/tenants/" + tenant + "/payment-proofs").param("overdue", "true"), service, 200));

        assertThat(recorded.get("reservationStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(again.get("decidedAt")).isEqualTo(recorded.get("decidedAt"));
        assertThat(overdue.at("/meta/total").asInt()).isZero();
        var cash = Map.of("paymentMethod", "CASH", "amountCents", 10_000L);
        JsonNode refused = CatalogHttpTest.json(exchange(post(BASE + "/reservations/" + id + "/payments").header("Idempotency-Key", "pay-jdbc-0012")
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(cash)), admin(), 422));
        assertThat(refused.get("message").asText()).contains("cash session");
    }
}
