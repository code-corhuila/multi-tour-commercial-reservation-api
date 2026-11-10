package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerAccountLink;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.FinancialDecisionOpening;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-017 over HTTP with every real JDBC bean (api tasks T-05; AC-17.2, AC-17.4, AC-17.8, AC-17.13, AC-17.14, AC-17.15): the Administrator records the 100 %
// credit of a decision, staff and the owner customer apply a credit to a reservation of the same customer as a validated payment, a repeat with the same
// Idempotency-Key answers the stored record, and the other roles, other customers and other tenants are refused before anything changes. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
@Import(CreditBalanceWriteJdbcHttpTest.FixedClock.class)
class CreditBalanceWriteJdbcHttpTest {
    static final String BASE = "/api/v1/commercial-reservation";
    static final String ADMIN = "TENANT_ADMINISTRATOR";

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock creditJdbcClock() { return Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC); }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogServiceRepository catalog;
    @Autowired CustomerRepository customers;
    @Autowired CustomerAccountLink links;
    @Autowired FinancialDecisionOpening opening;

    final UUID tenant = UUID.randomUUID(), tour = UUID.randomUUID(), departure = UUID.randomUUID(), customer = UUID.randomUUID();
    String source, target;

    @BeforeEach void seed() throws Exception {
        OffsetDateTime now = OffsetDateTime.ofInstant(TestJwtKeys.NOW, ZoneOffset.UTC);
        jdbc.update("INSERT INTO commercial_reservation.tenant_status (tenant_id, identifier, status, time_zone, country, "
                + "last_event_occurred_at, created_at, updated_at) VALUES (?, ?, 'ACTIVE', 'America/Bogota', 'CO', ?, ?, ?)", tenant, "t-" + tenant, now, now, now);
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio", CommercialTerms.of(100_000L, 40_000L, 20, false, false),
                TestJwtKeys.NOW));
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, hora_inicio, status, "
                + "effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 4)", UUID.randomUUID(), tenant, departure, tour, now.plusDays(1));
        customers.save(Customer.registerLead(customer, tenant, "CC12345", "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("persona@example.invalid", "3000000000"), true, TestJwtKeys.NOW));
        var row = Map.of("paymentType", "DEPOSIT", "initialWaitHours", 24, "additionalWaitHours", 48, "minimumDepositBp", 3000, "transferEnabled", true,
                "cashEnabled", true);
        exchange(put(BASE + "/payment-parameters").header("Idempotency-Key", "param-credit-0001").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("reason", "Opening season", "parameters", List.of(row)))), token(ADMIN), 200);
        source = reservation("source-reservation-key");
        target = reservation("target-reservation-key");
    }

    MvcResult exchange(MockHttpServletRequestBuilder request, String bearer, int code) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    String token(String role) throws Exception { return CatalogHttpTest.token(tenant.toString(), role); }

    String reservation(String key) throws Exception {
        var body = Map.of("serviceId", tour.toString(), "salidaId", departure.toString(), "people", 3, "customerId", customer.toString(), "paymentType", "DEPOSIT",
                "paymentMethod", "TRANSFER");
        return CatalogHttpTest.json(exchange(post(BASE + "/reservations").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body)), token(ADMIN), 201)).get("reservationId").asText();
    }

    UUID openDecision(long cents) {
        opening.open(tenant, UUID.fromString(source), new Cents(cents), new Cents(0), TestJwtKeys.NOW);
        return jdbc.queryForObject("SELECT financial_decision_id FROM commercial_reservation.financial_decision WHERE reservation_id = ?::uuid ORDER BY recognized_at DESC LIMIT 1",
                UUID.class, source);
    }

    JsonNode credit(String key) throws Exception {
        return CatalogHttpTest.json(exchange(post(BASE + "/financial-decisions/" + openDecision(80_000) + "/credit-decision").header("Idempotency-Key", key), token(ADMIN), 200));
    }

    MockHttpServletRequestBuilder apply(String credit, String reservation, long cents, String key) throws Exception {
        var request = post(BASE + "/credit-balances/" + credit + "/applications").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("targetReservationId", reservation, "amountCents", cents)));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    long pending(String reservation) {
        return jdbc.queryForObject("SELECT pending_balance_cents FROM commercial_reservation.reservation WHERE reservation_id = ?::uuid", Long.class, reservation);
    }

    @Test void theAdministratorRecordsTheCreditOfADecisionOnceAndARepeatAnswersTheStoredOne() throws Exception {
        UUID decision = openDecision(80_000);
        var route = post(BASE + "/financial-decisions/" + decision + "/credit-decision");

        var first = CatalogHttpTest.json(exchange(route.header("Idempotency-Key", "credit-key-0001"), token(ADMIN), 200));
        var again = CatalogHttpTest.json(exchange(post(BASE + "/financial-decisions/" + decision + "/credit-decision").header("Idempotency-Key", "credit-key-0001"),
                token(ADMIN), 200));

        assertThat(first.get("recordedCents").asLong()).isEqualTo(80_000);
        assertThat(first.get("availableCents").asLong()).isEqualTo(80_000);
        assertThat(first.get("origin").asText()).isEqualTo("CUSTOMER_CANCELLATION");
        assertThat(first.get("customerId").asText()).isEqualTo(customer.toString());
        assertThat(again).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.credit_balance WHERE tenant_id = ?", Integer.class, tenant)).isEqualTo(1);
        exchange(post(BASE + "/financial-decisions/" + decision + "/credit-decision").header("Idempotency-Key", "credit-key-0002"), token(ADMIN), 422);
        for (String role : List.of("OPERATIONAL_COLLABORATOR", "MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER")) {
            exchange(post(BASE + "/financial-decisions/" + decision + "/credit-decision").header("Idempotency-Key", "credit-key-0003"), token(role), 403);
        }
    }

    @Test void staffAppliesTheCreditAsAValidatedPaymentAndARepeatLeavesOneApplication() throws Exception {
        String credit = credit("credit-key-0004").get("creditBalanceId").asText();
        long before = pending(target);

        var first = CatalogHttpTest.json(exchange(apply(credit, target, 30_000, "apply-key-00001"), token(ADMIN), 201));
        var again = CatalogHttpTest.json(exchange(apply(credit, target, 30_000, "apply-key-00001"), token(ADMIN), 200));

        assertThat(first.get("amountCents").asLong()).isEqualTo(30_000);
        assertThat(first.get("appliedAs").asText()).isEqualTo("STAFF");
        assertThat(first.get("targetReservationId").asText()).isEqualTo(target);
        assertThat(again).isEqualTo(first);
        assertThat(pending(target)).isEqualTo(before - 30_000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.credit_balance_application WHERE credit_balance_id = ?::uuid", Integer.class,
                credit)).isEqualTo(1);
        exchange(apply(credit, target, 50_001, "apply-key-00002"), token(ADMIN), 422);
        assertThat(pending(target)).isEqualTo(before - 30_000);
    }

    @Test void theOwnerCustomerAppliesAsCustomerAndAnotherAccountLooksMissing() throws Exception {
        String credit = credit("credit-key-0005").get("creditBalanceId").asText();
        exchange(apply(credit, target, 10_000, "apply-key-00003"), token("FINAL_CUSTOMER"), 404);
        assertThat(links.linkIfUnlinked(tenant, customer, UUID.fromString(TestJwtKeys.ACCOUNT))).isTrue();

        var applied = CatalogHttpTest.json(exchange(apply(credit, target, 10_000, "apply-key-00004"), token("FINAL_CUSTOMER"), 201));

        assertThat(applied.get("appliedAs").asText()).isEqualTo("CUSTOMER");
        assertThat(applied.get("appliedBy").asText()).isEqualTo(TestJwtKeys.ACCOUNT);
    }

    @Test void managersAccountantsAndAnalystsAreRefusedAndBadRequestsNameTheirField() throws Exception {
        String credit = credit("credit-key-0006").get("creditBalanceId").asText();
        long before = pending(target);
        for (String role : List.of("MANAGER", "ACCOUNTANT", "ANALYST")) exchange(apply(credit, target, 10_000, "apply-key-00005"), token(role), 403);
        CatalogHttpTest.assertField(exchange(apply(credit, target, 0, "apply-key-00006"), token(ADMIN), 400), "amountCents");
        CatalogHttpTest.assertField(exchange(apply(credit, target, 10_000, null), token(ADMIN), 400), "Idempotency-Key");
        exchange(apply(UUID.randomUUID().toString(), target, 10_000, "apply-key-00007"), token(ADMIN), 404);
        exchange(apply(credit, UUID.randomUUID().toString(), 10_000, "apply-key-00008"), token(ADMIN), 404);
        exchange(apply(credit, target, 10_000, "apply-key-00009"), CatalogHttpTest.token(UUID.randomUUID().toString(), ADMIN), 404);
        assertThat(pending(target)).isEqualTo(before);
    }
}
