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
}
