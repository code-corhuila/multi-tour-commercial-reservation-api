package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Spec 027 over the route receiveEvent: SalidaRescheduled moves the row (201, 200 on a repeat), waits for the row (503), and refuses a change of service (422,
// alternative B detected, not built). Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, DepartureEventHttpFixture.class})
class DepartureRescheduleHttpTest {
    static final UUID TENANT = UUID.fromString("0b6f2c3d-5e41-4a97-8d20-1f7a9c3e6b84");
    static final String ROUTE = "/api/v1/commercial-reservation/internal/tenants/" + TENANT + "/events";
    static final Instant STARTS = Instant.parse("2027-03-01T13:00:00Z");
    static final Instant NEW_START = Instant.parse("2027-03-08T14:00:00Z");
    static final Instant NEW_END = Instant.parse("2027-03-08T18:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired TenantEventHttpFixture.MemoryTenants events;
    @Autowired DepartureEventHttpFixture.MemoryDepartures departures;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;
    UUID tour, salida;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach void plant() {
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
        events.clear();
        departures.clear();
        tour = UUID.randomUUID();
        salida = UUID.randomUUID();
        catalog.save(CatalogService.register(tour, TENANT, CatalogServiceType.TOUR, "Caminata Ficticia",
                CommercialTerms.of(100_000L, 50_000L, 12, false, false), Instant.parse("2026-10-01T00:00:00Z")));
        departures.save(new DepartureState(salida, TENANT, tour, STARTS, DepartureStatus.SCHEDULED, 12, 3, Instant.parse("2026-11-03T09:00:00Z")));
    }

    @AfterEach void release() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }

    private Map<String, Object> rescheduled(UUID id, UUID serviceId, Instant start, Instant end) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("salidaId", salida.toString());
        payload.put("serviceId", serviceId.toString());
        payload.put("startsAt", start.toString());
        payload.put("estimatedEndsAt", end.toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", id.toString());
        body.put("eventType", "SalidaRescheduled");
        body.put("eventVersion", 1);
        body.put("tenantId", TENANT.toString());
        body.put("aggregateId", salida.toString());
        body.put("payload", payload);
        body.put("occurredAt", "2026-11-03T10:00:00Z");
        return body;
    }

    private MockHttpServletRequestBuilder post(Map<String, Object> body) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of("SERVICE"));
        String token = TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
        return post(ROUTE).header("X-Correlation-Id", "reschedule-test").contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsString(body)).header("Idempotency-Key", body.get("eventId").toString())
                .header("Authorization", "Bearer " + token);
    }

    @Test void updatesTheStartAndTheEndOfTheRowAndAnswersOkOnARepeat() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post(rescheduled(id, tour, NEW_START, NEW_END))).andExpect(status().isCreated());
        mvc.perform(post(rescheduled(id, tour, NEW_START, NEW_END))).andExpect(status().isOk());
        assertThat(departures.rows.get(salida).startsAt()).isEqualTo(NEW_START);
        assertThat(departures.ends.get(salida)).isEqualTo(NEW_END);
        assertThat(departures.rows.get(salida).heldSeats()).isEqualTo(3);
        assertThat(events.processed).containsExactly(id);
    }

    @Test void waitsForTheScheduledEventBeforeARescheduleEvent() throws Exception {
        departures.clear();
        mvc.perform(post(rescheduled(UUID.randomUUID(), tour, NEW_START, NEW_END))).andExpect(status().isServiceUnavailable());
        assertThat(departures.rows).isEmpty();
        assertThat(events.processed).isEmpty();
    }

    @Test void refusesAnEndThatIsNotAfterTheStartAndAnotherTenant() throws Exception {
        mvc.perform(post(rescheduled(UUID.randomUUID(), tour, NEW_START, NEW_START))).andExpect(status().isBadRequest());
        Map<String, Object> foreign = rescheduled(UUID.randomUUID(), tour, NEW_START, NEW_END);
        foreign.put("tenantId", UUID.randomUUID().toString());
        mvc.perform(post(foreign)).andExpect(status().isBadRequest());
        Map<String, Object> noService = rescheduled(UUID.randomUUID(), tour, NEW_START, NEW_END);
        ((Map<?, ?>) noService.get("payload")).remove("serviceId");
        mvc.perform(post(noService)).andExpect(status().isBadRequest());
        assertThat(departures.rows.get(salida).startsAt()).isEqualTo(STARTS);
        assertThat(events.processed).isEmpty();
    }

    @Test void refusesARescheduleThatChangesTheServiceWithAnExplicitConflictAndNeverLogsIds() throws Exception {
        UUID other = UUID.randomUUID();
        mvc.perform(post(rescheduled(UUID.randomUUID(), other, NEW_START, NEW_END)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(other.toString()))));
        assertThat(departures.rows.get(salida).startsAt()).isEqualTo(STARTS);
        assertThat(events.processed).isEmpty();
        assertThat(logs.list).noneMatch(entry -> entry.getLevel().isGreaterOrEqual(Level.ERROR))
                .noneMatch(entry -> entry.getFormattedMessage().contains(other.toString()) || entry.getFormattedMessage().contains(salida.toString()));
    }
}
