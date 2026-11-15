package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

// HU-COM-006 AC-06.14 and AC-06.16 over the route receiveEvent of the contract: the five Salida events feed the local row
// of the departure. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, DepartureEventHttpFixture.class})
class DepartureEventsHttpTest {
    static final UUID TENANT = UUID.fromString("0b6f2c3d-5e41-4a97-8d20-1f7a9c3e6b84");
    static final String ROUTE = "/api/v1/commercial-reservation/internal/tenants/" + TENANT + "/events";
    static final Instant STARTS = Instant.parse("2027-03-01T13:00:00Z");
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
    }

    @AfterEach void release() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }

    private Map<String, Object> event(UUID id, String type, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("salidaId", salida.toString());
        payload.putAll(extra);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", id.toString());
        body.put("eventType", type);
        body.put("eventVersion", 1);
        body.put("tenantId", TENANT.toString());
        body.put("aggregateId", salida.toString());
        body.put("payload", payload);
        body.put("occurredAt", "2026-11-03T10:00:00Z");
        return body;
    }

    private Map<String, Object> scheduled(UUID id) {
        return event(id, "SalidaScheduled", Map.of("serviceId", tour.toString(), "startsAt", STARTS.toString(),
                "estimatedEndsAt", "2027-03-01T17:00:00Z", "status", "SCHEDULED"));
    }

    private Map<String, Object> simple(String type) {
        return event(UUID.randomUUID(), type, Map.of());
    }

    private Map<String, Object> vehicles(int total) {
        return event(UUID.randomUUID(), "SalidaVehiclesChanged", Map.of("vehicleTotalCapacity", total));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(Map<String, Object> body) {
        return (Map<String, Object>) body.get("payload");
    }

    private String token(String role) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of(role));
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private void deliver(Map<String, Object> body, String role, int expected) throws Exception {
        MockHttpServletRequestBuilder request = post(ROUTE).header("X-Correlation-Id", "departure-events-test")
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsString(body))
                .header("Idempotency-Key", body.get("eventId").toString());
        if (role != null) {
            request.header("Authorization", "Bearer " + token(role));
        }
        mvc.perform(request).andExpect(status().is(expected));
    }

    @Test void aDepartureEventPostedTwiceAnswersCreatedThenOk() throws Exception {
        UUID id = UUID.randomUUID();
        deliver(scheduled(id), "SERVICE", 201);
        deliver(scheduled(id), "SERVICE", 200);
        assertThat(departures.rows).hasSize(1);
        assertThat(events.processed).containsExactly(id);
    }

    @Test void aScheduledEventPostedOverHttpCreatesTheRowTheOfferReads() throws Exception {
        deliver(scheduled(UUID.randomUUID()), "SERVICE", 201);
        var open = departures.findOpen(TENANT, tour, Instant.parse("2027-02-01T00:00:00Z"));
        assertThat(open).hasSize(1);
        assertThat(open.get(0).departureId()).isEqualTo(salida);
        assertThat(open.get(0).startsAt()).isEqualTo(STARTS);
        assertThat(open.get(0).status()).isEqualTo(DepartureStatus.SCHEDULED);
        assertThat(open.get(0).effectiveCapacity()).isEqualTo(12);
    }

    @Test void aVehicleTotalBelowTheHeldSeatsKeepsTheHeldSeats() throws Exception {
        departures.save(new DepartureState(salida, TENANT, tour, STARTS, DepartureStatus.SCHEDULED, 12, 5));
        deliver(vehicles(3), "SERVICE", 201);
        assertThat(departures.rows.get(salida).effectiveCapacity()).isEqualTo(5);
        deliver(vehicles(8), "SERVICE", 201);
        assertThat(departures.rows.get(salida).effectiveCapacity()).isEqualTo(8);
        assertThat(departures.rows.get(salida).heldSeats()).isEqualTo(5);
    }

    @Test void aLateDepartureEventAnswersOkAndChangesNothing() throws Exception {
        departures.save(new DepartureState(salida, TENANT, tour, STARTS, DepartureStatus.FINALIZED, 12, 2));
        deliver(simple("SalidaExecutionStarted"), "SERVICE", 200);
        deliver(simple("SalidaCancelled"), "SERVICE", 200);
        assertThat(departures.rows.get(salida).status()).isEqualTo(DepartureStatus.FINALIZED);
    }

    // DP-16 and DD-8 (answered by the Project Lead): an event that arrives before SalidaScheduled answers 503 so the relay
    // retries with growing waits; no provisional row is created and the event is not marked as received.
    @Test void aDepartureEventForADepartureWithNoRowAnswersRetryLaterAndCanBeRedelivered() throws Exception {
        Map<String, Object> early = event(UUID.randomUUID(), "SalidaCancelled", Map.of());
        deliver(early, "SERVICE", 503);
        assertThat(departures.rows).isEmpty();
        assertThat(events.processed).isEmpty();
        deliver(scheduled(UUID.randomUUID()), "SERVICE", 201);
        deliver(early, "SERVICE", 201);
        assertThat(departures.rows.get(salida).status()).isEqualTo(DepartureStatus.CANCELLED);
    }

    @Test void theRetryLaterAnswerHasTheShapeOfTheErrorHandlerAndAWaitHint() throws Exception {
        Map<String, Object> early = event(UUID.randomUUID(), "SalidaVehiclesChanged", Map.of("vehicleTotalCapacity", 9));
        mvc.perform(post(ROUTE).header("X-Correlation-Id", "departure-events-test").contentType(MediaType.APPLICATION_JSON)
                        .content(TestJwtKeys.JSON.writeValueAsString(early)).header("Idempotency-Key", early.get("eventId").toString())
                        .header("Authorization", "Bearer " + token("SERVICE")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("deliver the event again later"))
                .andExpect(jsonPath("$.traceId").value("departure-events-test"));
    }

    @Test void aRetryLaterAnswerIsLoggedAsAWarningWithTheEventIdOnly() throws Exception {
        UUID id = UUID.randomUUID();
        deliver(event(id, "SalidaFinalized", Map.of()), "SERVICE", 503);
        assertThat(logs.list).noneMatch(entry -> entry.getLevel().isGreaterOrEqual(Level.ERROR));
        var held = logs.list.stream().filter(entry -> entry.getLevel() == Level.WARN
                && entry.getFormattedMessage().contains(id.toString())).toList();
        assertThat(held).hasSize(1);
        assertThat(held.get(0).getThrowableProxy()).isNull();
        assertThat(held.get(0).getFormattedMessage()).doesNotContain(salida.toString()).doesNotContain(TENANT.toString());
    }

    @Test void theOccurredAtOfTheEventIsStoredAsTheMarkerOfTheRow() throws Exception {
        Map<String, Object> first = scheduled(UUID.randomUUID());
        first.put("occurredAt", "2026-11-03T10:00:00Z");
        deliver(first, "SERVICE", 201);
        assertThat(departures.rows.get(salida).lastEventOccurredAt()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
        Map<String, Object> started = simple("SalidaExecutionStarted");
        started.put("occurredAt", "2027-03-01T13:05:00Z");
        deliver(started, "SERVICE", 201);
        assertThat(departures.rows.get(salida).lastEventOccurredAt()).isEqualTo(Instant.parse("2027-03-01T13:05:00Z"));
    }

    @Test void anOlderVehicleEventAnswersOkAndNeverOverwritesTheNewerCapacity() throws Exception {
        departures.save(new DepartureState(salida, TENANT, tour, STARTS, DepartureStatus.SCHEDULED, 12, 0,
                Instant.parse("2026-11-03T10:00:00Z")));
        Map<String, Object> newer = vehicles(10);
        newer.put("occurredAt", "2026-11-03T12:00:00Z");
        deliver(newer, "SERVICE", 201);
        Map<String, Object> older = vehicles(4);
        older.put("occurredAt", "2026-11-03T11:00:00Z");
        deliver(older, "SERVICE", 200);
        assertThat(departures.rows.get(salida).effectiveCapacity()).isEqualTo(10);
        assertThat(departures.rows.get(salida).lastEventOccurredAt()).isEqualTo(Instant.parse("2026-11-03T12:00:00Z"));
    }

    @Test void aDepartureEventWithoutAValidOccurredAtIsRefused() throws Exception {
        Map<String, Object> missing = scheduled(UUID.randomUUID());
        missing.remove("occurredAt");
        deliver(missing, "SERVICE", 400);
        Map<String, Object> invalid = scheduled(UUID.randomUUID());
        invalid.put("occurredAt", "yesterday");
        deliver(invalid, "SERVICE", 400);
        assertThat(departures.rows).isEmpty();
        assertThat(events.processed).isEmpty();
    }

    @Test void aPersonTokenCannotPostADepartureEvent() throws Exception {
        deliver(scheduled(UUID.randomUUID()), null, 401);
        deliver(scheduled(UUID.randomUUID()), "TENANT_ADMINISTRATOR", 403);
        assertThat(departures.rows).isEmpty();
        assertThat(events.processed).isEmpty();
    }

    @Test void aDepartureEventOfAnotherTenantThanThePathIsRefused() throws Exception {
        Map<String, Object> foreign = scheduled(UUID.randomUUID());
        foreign.put("tenantId", UUID.randomUUID().toString());
        deliver(foreign, "SERVICE", 400);
        Map<String, Object> payloadForeign = scheduled(UUID.randomUUID());
        payloadOf(payloadForeign).put("tenantId", UUID.randomUUID().toString());
        deliver(payloadForeign, "SERVICE", 400);
        Map<String, Object> notTheDeparture = scheduled(UUID.randomUUID());
        notTheDeparture.put("aggregateId", UUID.randomUUID().toString());
        deliver(notTheDeparture, "SERVICE", 400);
        Map<String, Object> noTotal = vehicles(4);
        payloadOf(noTotal).remove("vehicleTotalCapacity");
        deliver(noTotal, "SERVICE", 400);
        assertThat(departures.rows).isEmpty();
        assertThat(events.processed).isEmpty();
    }
}
