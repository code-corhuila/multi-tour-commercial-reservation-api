package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-017 reads over HTTP with every real JDBC bean (api tasks T-05; AC-17.9, AC-17.13, AC-17.16): the list, the detail with its history and the summary show the
// derived values, staff reads everything of its tenant, the Final Customer only their own credit, and another tenant sees nothing. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
@Import(CreditBalanceWriteJdbcHttpTest.FixedClock.class)
class CreditBalanceReadJdbcHttpTest {
    static final String BASE = "/api/v1/commercial-reservation";
    static final String ADMIN = "TENANT_ADMINISTRATOR";

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

    MockHttpServletRequestBuilder read(String route) {
        return get(BASE + route);
    }

    @Test void theListShowsRecordedAppliedInRefundAndAvailableAndFiltersByAvailableValue() throws Exception {
        String credit = credit("credit-key-1001").get("creditBalanceId").asText();
        exchange(apply(credit, target, 30_000, "apply-key-10001"), token(ADMIN), 201);

        var all = CatalogHttpTest.json(exchange(read("/credit-balances"), token("ANALYST"), 200));
        var row = all.get("data").get(0);

        assertThat(all.get("data")).hasSize(1);
        assertThat(all.get("meta").get("total").asLong()).isEqualTo(1);
        assertThat(row.get("recordedCents").asLong()).isEqualTo(80_000);
        assertThat(row.get("appliedCents").asLong()).isEqualTo(30_000);
        assertThat(row.get("inRefundCents").asLong()).isEqualTo(0);
        assertThat(row.get("availableCents").asLong()).isEqualTo(50_000);
        assertThat(CatalogHttpTest.json(exchange(read("/credit-balances?available=true"), token(ADMIN), 200)).get("data")).hasSize(1);
        exchange(apply(credit, target, 50_000, "apply-key-10002"), token(ADMIN), 201);
        assertThat(CatalogHttpTest.json(exchange(read("/credit-balances?available=true"), token(ADMIN), 200)).get("data")).isEmpty();
        assertThat(CatalogHttpTest.json(exchange(read("/credit-balances?customerId=" + UUID.randomUUID()), token(ADMIN), 200)).get("data")).isEmpty();
        assertThat(CatalogHttpTest.json(exchange(read("/credit-balances?customerId=" + customer), token(ADMIN), 200)).get("data")).hasSize(1);
    }

    @Test void theDetailCarriesTheHistoryOfApplicationsAndAnotherTenantLooksMissing() throws Exception {
        String credit = credit("credit-key-1002").get("creditBalanceId").asText();
        exchange(apply(credit, target, 30_000, "apply-key-10003"), token(ADMIN), 201);

        var detail = CatalogHttpTest.json(exchange(read("/credit-balances/" + credit), token("ACCOUNTANT"), 200));

        assertThat(detail.get("availableCents").asLong()).isEqualTo(50_000);
        assertThat(detail.get("applications")).hasSize(1);
        assertThat(detail.get("applications").get(0).get("targetReservationId").asText()).isEqualTo(target);
        assertThat(detail.get("applications").get(0).get("appliedAs").asText()).isEqualTo("STAFF");
        assertThat(detail.get("refundRequests")).isEmpty();
        exchange(read("/credit-balances/" + credit), CatalogHttpTest.token(UUID.randomUUID().toString(), ADMIN), 404);
        exchange(read("/credit-balances/" + UUID.randomUUID()), token(ADMIN), 404);
    }

    @Test void aFinalCustomerReadsOnlyTheOwnCreditAndAnUnlinkedAccountSeesNothing() throws Exception {
        String credit = credit("credit-key-1003").get("creditBalanceId").asText();

        assertThat(CatalogHttpTest.json(exchange(read("/credit-balances"), token("FINAL_CUSTOMER"), 200)).get("data")).isEmpty();
        exchange(read("/credit-balances/" + credit), token("FINAL_CUSTOMER"), 404);
        assertThat(links.linkIfUnlinked(tenant, customer, UUID.fromString(TestJwtKeys.ACCOUNT))).isTrue();

        var mine = CatalogHttpTest.json(exchange(read("/credit-balances?customerId=" + UUID.randomUUID()), token("FINAL_CUSTOMER"), 200));
        assertThat(mine.get("data")).hasSize(1);
        assertThat(mine.get("data").get(0).get("customerId").asText()).isEqualTo(customer.toString());
        exchange(read("/credit-balances/" + credit), token("FINAL_CUSTOMER"), 200);
    }

    @Test void theSummaryIsForStaffOnlyAndCountsTheAvailableValueAndTheCustomers() throws Exception {
        String credit = credit("credit-key-1004").get("creditBalanceId").asText();
        exchange(apply(credit, target, 30_000, "apply-key-10004"), token(ADMIN), 201);

        var summary = CatalogHttpTest.json(exchange(read("/credit-balance-summary"), token("MANAGER"), 200));

        assertThat(summary.get("availableCents").asLong()).isEqualTo(50_000);
        assertThat(summary.get("customersWithCredit").asInt()).isEqualTo(1);
        assertThat(summary.get("requestsToDecide").asInt()).isEqualTo(0);
        exchange(read("/credit-balance-summary"), token("FINAL_CUSTOMER"), 403);
        assertThat(CatalogHttpTest.json(exchange(read("/credit-balance-summary"), CatalogHttpTest.token(UUID.randomUUID().toString(), ADMIN), 200))
                .get("availableCents").asLong()).isEqualTo(0);
    }

    @Test void anUnknownQueryParameterOrAnInvalidFlagIsRefusedNamingTheField() throws Exception {
        CatalogHttpTest.assertField(exchange(read("/credit-balances?color=red"), token(ADMIN), 400), "color");
        CatalogHttpTest.assertField(exchange(read("/credit-balances?available=maybe"), token(ADMIN), 400), "available");
        CatalogHttpTest.assertField(exchange(read("/credit-balances?limit=101"), token(ADMIN), 400), "limit");
    }
}
