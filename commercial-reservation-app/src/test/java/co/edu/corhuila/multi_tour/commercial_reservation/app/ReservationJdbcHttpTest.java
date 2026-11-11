package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-007 and HU-COM-008 over HTTP with every real JDBC bean and no fake: the routes answer on the happy path with rows
// seeded in tenant_status, catalog_service, salida_capacity and customer. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
@Import(ReservationJdbcHttpTest.FixedClock.class)
class ReservationJdbcHttpTest {
    static final String BASE = "/api/v1/commercial-reservation";
    static final String ADMIN = "TENANT_ADMINISTRATOR";

    public static class Database implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            new TestJwtKeys().initialize(context);
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "DB_URL=" + System.getenv("TEST_DATABASE_URL"), "DB_USER=" + System.getenv("TEST_DATABASE_USER"),
                    "DB_PASSWORD=" + System.getenv("TEST_DATABASE_PASSWORD"));
        }
    }

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock reservationJdbcClock() { return Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC); }
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
                + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 7)",
                UUID.randomUUID(), tenant, departure, tour, now.plusDays(1));
        customers.save(Customer.registerLead(customer, tenant, "CC12345", "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("persona@example.invalid", "3000000000"), true, TestJwtKeys.NOW));
        enable("DEPOSIT", 3000); // DP-11: a tenant with no payment condition offers no type, so the creation needs the rows
        enable("FULL_PAYMENT", null);
    }

    void enable(String type, Integer minimumBp) {
        jdbc.update("INSERT INTO commercial_reservation.payment_condition (payment_condition_id, tenant_id, service_id, payment_type, "
                + "initial_wait_hours, additional_wait_hours, minimum_deposit_bp, balance_deadline_advance_hours, transfer_enabled, "
                + "cash_enabled, changed_by, change_reason, changed_at) VALUES (?, ?, null, ?, 24, 12, ?, 48, true, true, ?, 'test', now())",
                UUID.randomUUID(), tenant, type, minimumBp, UUID.randomUUID());
    }

    MvcResult exchange(MockHttpServletRequestBuilder request, String role, int code) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + CatalogHttpTest.token(tenant.toString(), role))
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    Map<String, Object> quoteBody(int people) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceId", tour.toString()); body.put("salidaId", departure.toString()); body.put("people", people);
        return body;
    }

    MockHttpServletRequestBuilder quote(Map<String, Object> body) throws Exception {
        return post(BASE + "/reservation-quotes").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
    }

    @Test void theDeparturesOfTheTourAreListedFromTheCapacityProjection() throws Exception {
        JsonNode page = CatalogHttpTest.json(exchange(get(BASE + "/catalog/services/" + tour + "/departures"), ADMIN, 200));

        assertThat(page.at("/data/0/departureId").asText()).isEqualTo(departure.toString());
        assertThat(page.at("/data/0/availableSeats").asInt()).isEqualTo(3);
        assertThat(page.at("/data/0/totalSeats").asInt()).isEqualTo(10);
        assertThat(page.at("/meta/total").asInt()).isEqualTo(1);
    }

    @Test void aQuoteChecksTheDepartureInTheDatabaseAndReturnsTheValueInCents() throws Exception {
        JsonNode quote = CatalogHttpTest.json(exchange(quote(quoteBody(3)), ADMIN, 200));

        assertThat(quote.at("/tourLine/amountCents").asLong()).isEqualTo(300_000L);
        assertThat(quote.get("projectedValueCents").asLong()).isEqualTo(300_000L);
        assertThat(quote.get("finalValueCents").asLong()).isEqualTo(300_000L);
    }

    @Test void aCreationAboveTheAvailableSeatsAndAQuoteOnAnUnknownDepartureAreRefusedNotAServerError() throws Exception {
        var tooMany = quoteBody(4);
        tooMany.put("customerId", customer.toString()); tooMany.put("paymentType", "DEPOSIT"); tooMany.put("paymentMethod", "TRANSFER");
        exchange(post(BASE + "/reservations").header("Idempotency-Key", "jdbc-seats-0001").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(tooMany)), ADMIN, 422);
        var unknown = quoteBody(1);
        unknown.put("salidaId", UUID.randomUUID().toString());
        exchange(quote(unknown), ADMIN, 404);
    }

    MockHttpServletRequestBuilder create(String key) throws Exception {
        var body = quoteBody(3);
        body.put("customerId", customer.toString()); body.put("paymentType", "DEPOSIT"); body.put("paymentMethod", "TRANSFER");
        return post(BASE + "/reservations").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
    }

    @Test void aReservationIsCreatedAndReadBackThroughTheListTheDetailAndTheBreakdown() throws Exception {
        JsonNode created = CatalogHttpTest.json(exchange(create("jdbc-create-0001"), ADMIN, 201));
        String id = created.get("reservationId").asText();
        assertThat(created.get("reservationStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(created.get("finalValueCents").asLong()).isEqualTo(300_000L);

        JsonNode list = CatalogHttpTest.json(exchange(get(BASE + "/reservations"), ADMIN, 200));
        assertThat(list.at("/meta/total").asInt()).isEqualTo(1);
        assertThat(list.at("/data/0/reservationId").asText()).isEqualTo(id);
        assertThat(list.at("/data/0/customerName").asText()).isEqualTo("Persona Ficticia");
        assertThat(list.at("/data/0/tourName").asText()).isEqualTo("Tour Ficticio");

        JsonNode detail = CatalogHttpTest.json(exchange(get(BASE + "/reservations/" + id), ADMIN, 200));
        assertThat(detail.get("projectedValueCents").asLong()).isEqualTo(300_000L);
        assertThat(detail.get("pendingBalanceCents").asLong()).isEqualTo(300_000L);
        assertThat(detail.get("seatHeld").asBoolean()).isFalse();

        JsonNode breakdown = CatalogHttpTest.json(exchange(get(BASE + "/reservations/" + id + "/discount-breakdown"), ADMIN, 200));
        assertThat(breakdown.at("/breakdown/finalValueCents").asLong()).isEqualTo(300_000L);
    }

    @Test void theSameKeyReplaysTheReservationInsteadOfCreatingASecondOne() throws Exception {
        JsonNode first = CatalogHttpTest.json(exchange(create("jdbc-create-0002"), ADMIN, 201));
        JsonNode replay = CatalogHttpTest.json(exchange(create("jdbc-create-0002"), ADMIN, 200));

        assertThat(replay.get("reservationId").asText()).isEqualTo(first.get("reservationId").asText());
        assertThat(CatalogHttpTest.json(exchange(get(BASE + "/reservations"), ADMIN, 200)).at("/meta/total").asInt()).isEqualTo(1);
    }

    // A second customer linked to the account of the token of the Final Customer; the seeded one has no account.
    UUID linkedCustomer() {
        UUID mine = UUID.randomUUID();
        customers.save(Customer.registerLead(mine, tenant, "CC67890", "Cliente Ficticio", BirthDateOrAge.ofAge(28),
                new ContactInfo("cliente@example.invalid", "3000000001"), true, TestJwtKeys.NOW)
                .linkedTo(UUID.fromString(TestJwtKeys.ACCOUNT)));
        return mine;
    }

    @Test void aFinalCustomerCreatesAndReadsOnlyTheReservationsOfTheirOwnCustomerRecord() throws Exception {
        UUID mine = linkedCustomer();
        String others = CatalogHttpTest.json(exchange(create("jdbc-own-0001"), ADMIN, 201)).get("reservationId").asText();
        var body = quoteBody(2);
        body.put("paymentType", "FULL_PAYMENT"); body.put("paymentMethod", "CASH");
        JsonNode own = CatalogHttpTest.json(exchange(post(BASE + "/reservations").header("Idempotency-Key", "jdbc-own-0002")
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body)), "FINAL_CUSTOMER", 201));
        assertThat(own.get("customerId").asText()).isEqualTo(mine.toString());

        JsonNode list = CatalogHttpTest.json(exchange(get(BASE + "/reservations"), "FINAL_CUSTOMER", 200));
        assertThat(list.at("/meta/total").asInt()).isEqualTo(1);
        assertThat(list.at("/data/0/reservationId").asText()).isEqualTo(own.get("reservationId").asText());
        exchange(get(BASE + "/reservations/" + own.get("reservationId").asText()), "FINAL_CUSTOMER", 200);
        exchange(get(BASE + "/reservations/" + others), "FINAL_CUSTOMER", 404);
        assertThat(CatalogHttpTest.json(exchange(get(BASE + "/reservations"), ADMIN, 200)).at("/meta/total").asInt()).isEqualTo(2);
    }

    @Test void anAccountWithNoCustomerRecordHasAnEmptyListAndNoDetail() throws Exception {
        String id = CatalogHttpTest.json(exchange(create("jdbc-own-0003"), ADMIN, 201)).get("reservationId").asText();

        JsonNode list = CatalogHttpTest.json(exchange(get(BASE + "/reservations"), "FINAL_CUSTOMER", 200));
        assertThat(list.at("/meta/total").asInt()).isZero();
        assertThat(list.get("data")).isEmpty();
        exchange(get(BASE + "/reservations/" + id), "FINAL_CUSTOMER", 404);
    }

    @Test void aReservationOfAnotherTenantIsNotFoundAndNotListed() throws Exception {
        String id = CatalogHttpTest.json(exchange(create("jdbc-create-0003"), ADMIN, 201)).get("reservationId").asText();
        String stranger = CatalogHttpTest.token(UUID.randomUUID().toString(), ADMIN);

        var detail = mvc.perform(get(BASE + "/reservations/" + id).header("Authorization", "Bearer " + stranger)).andReturn();
        var list = mvc.perform(get(BASE + "/reservations").header("Authorization", "Bearer " + stranger)).andReturn();
        assertThat(detail.getResponse().getStatus()).isEqualTo(404);
        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        assertThat(CatalogHttpTest.json(list).at("/meta/total").asInt()).isZero();
    }

    // HU-COM-007 DP-03: a tour that requires transport is quoted and created with the fare of its catalog row; with no fare it is refused (RN-15).
    UUID transportTour(Long rate) {
        UUID id = UUID.randomUUID();
        catalog.save(CatalogService.register(id, tenant, CatalogServiceType.TOUR, "Tour con Transporte",
                CommercialTerms.of(100_000L, 40_000L, 20, true, false, rate), TestJwtKeys.NOW));
        jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, "
                + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 0)",
                UUID.randomUUID(), tenant, UUID.randomUUID(), id, OffsetDateTime.ofInstant(TestJwtKeys.NOW, ZoneOffset.UTC).plusDays(1));
        return id;
    }

    Map<String, Object> bodyOf(UUID service, boolean forCreation) {
        UUID salida = jdbc.queryForObject("SELECT salida_id FROM commercial_reservation.salida_capacity WHERE service_id = ?",
                UUID.class, service);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceId", service.toString()); body.put("salidaId", salida.toString()); body.put("people", 3);
        if (forCreation) {
            body.put("customerId", customer.toString()); body.put("paymentType", "DEPOSIT"); body.put("paymentMethod", "TRANSFER");
        }
        return body;
    }

    MockHttpServletRequestBuilder createFor(UUID service, String key) throws Exception {
        return post(BASE + "/reservations").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(bodyOf(service, true)));
    }

    @Test void aTransportRequiredTourWithAParametrizedRateIsQuotedAndCreated() throws Exception {
        UUID withRate = transportTour(20_000L);

        JsonNode quote = CatalogHttpTest.json(exchange(quote(bodyOf(withRate, false)), ADMIN, 200));
        assertThat(quote.at("/transportLine/amountCents").asLong()).isEqualTo(60_000L);
        assertThat(quote.get("projectedValueCents").asLong()).isEqualTo(360_000L);

        JsonNode created = CatalogHttpTest.json(exchange(createFor(withRate, "jdbc-transport-0001"), ADMIN, 201));
        assertThat(created.get("finalValueCents").asLong()).isEqualTo(360_000L);
    }

    @Test void aTransportRequiredTourWithNoRateIsRefusedOnTheQuoteAndOnTheCreation() throws Exception {
        UUID noRate = transportTour(null);

        JsonNode error = CatalogHttpTest.json(exchange(quote(bodyOf(noRate, false)), ADMIN, 422));
        assertThat(error.get("message").asText()).contains("transport");
        exchange(createFor(noRate, "jdbc-transport-0002"), ADMIN, 422);
    }
}
