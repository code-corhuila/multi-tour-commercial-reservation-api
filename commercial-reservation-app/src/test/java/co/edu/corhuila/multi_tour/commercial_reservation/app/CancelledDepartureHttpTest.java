package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

// Spec 023 (-api), T-03: SalidaCancelled over the route receiveEvent also opens the episodes of the alive reservations of the departure (AC-23.1, AC-23.4, AC-23.6, AC-23.8 to
// AC-23.10). Over memory fakes; the keyset query has its own persistence test. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, DepartureEventHttpFixture.class, ReschedulingHttpFixture.class, CancelledDepartureHttpTest.Alive.class})
class CancelledDepartureHttpTest {
    static final UUID TENANT = UUID.fromString("0b6f2c3d-5e41-4a97-8d20-1f7a9c3e6b84");
    static final String ROUTE = "/api/v1/commercial-reservation/internal/tenants/" + TENANT + "/events";

    @TestConfiguration
    static class Alive {
        static final class Rows implements DepartureReservationReader {
            final List<UUID> ids = new ArrayList<>();
            @Override public List<UUID> findAliveByDeparture(UUID tenantId, UUID departureId, UUID after, int limit) {
                return ids.stream().filter(id -> after == null || id.compareTo(after) > 0).sorted().limit(limit).toList();
            }
        }
        @Bean @Primary Rows aliveRows() { return new Rows(); }
    }

    @Autowired MockMvc mvc;
    @Autowired TenantEventHttpFixture.MemoryTenants events;
    @Autowired DepartureEventHttpFixture.MemoryDepartures departures;
    @Autowired ReschedulingHttpFixture.MemoryEpisodes episodes;
    @Autowired Alive.Rows alive;
    private final UUID salida = new UUID(23, 10), tour = new UUID(23, 11);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach void plant() {
        events.clear();
        departures.clear();
        episodes.clear();
        alive.ids.clear();
        departures.save(new DepartureState(salida, TENANT, tour, Instant.parse("2027-03-01T13:00:00Z"), DepartureStatus.SCHEDULED, 12, 2));
        for (int i = 1; i <= 2; i++) {
            alive.ids.add(new UUID(230, i));
            episodes.reservations.add(new UUID(230, i));
        }
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    @AfterEach void release() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }

    private Map<String, Object> cancelled(UUID eventId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("salidaId", salida.toString());
        payload.put("cancelledAt", "2026-11-12T15:00:00Z");
        payload.put("cause", "NOT_ATTRIBUTABLE_TO_CUSTOMER");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId.toString());
        body.put("eventType", "SalidaCancelled");
        body.put("eventVersion", 1);
        body.put("tenantId", TENANT.toString());
        body.put("aggregateId", salida.toString());
        body.put("payload", payload);
        body.put("occurredAt", "2026-11-12T15:00:00Z");
        return body;
    }

    private void deliver(Map<String, Object> body, int expected) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of("SERVICE"));
        String token = TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
        mvc.perform(post(ROUTE).header("X-Correlation-Id", "cancelled-departure-test").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsString(body)).header("Idempotency-Key", body.get("eventId").toString()))
                .andExpect(status().is(expected));
    }

    @Test void opensTheEpisodesOfTheAliveReservationsAndARedeliveryAnswersOk() throws Exception {
        UUID eventId = UUID.randomUUID();

        deliver(cancelled(eventId), 201);

        assertThat(departures.rows.get(salida).status()).isEqualTo(DepartureStatus.CANCELLED);
        assertThat(episodes.episodes.values()).extracting(e -> e.reservationId()).containsExactlyInAnyOrder(new UUID(230, 1), new UUID(230, 2));
        assertThat(episodes.episodes.values()).allSatisfy(e -> assertThat(e.cancelledAt()).isEqualTo(Instant.parse("2026-11-12T15:00:00Z")));
        assertThat(events.processed).contains(eventId);

        deliver(cancelled(eventId), 200);
        assertThat(episodes.episodes).hasSize(2);
    }

    @Test void countsTheClocksFromTheCancelledAtOfThePayloadAndFallsBackToTheEnvelopeWhenItIsMissing() throws Exception {
        Map<String, Object> withPayloadInstant = cancelled(UUID.randomUUID());
        ((Map<String, Object>) withPayloadInstant.get("payload")).put("cancelledAt", "2026-11-12T14:00:00Z");

        deliver(withPayloadInstant, 201);

        assertThat(episodes.episodes.values()).hasSize(2).allSatisfy(e -> assertThat(e.cancelledAt()).isEqualTo(Instant.parse("2026-11-12T14:00:00Z")));

        episodes.clear();
        departures.save(new DepartureState(salida, TENANT, tour, Instant.parse("2027-03-01T13:00:00Z"), DepartureStatus.SCHEDULED, 12, 2));
        Map<String, Object> withoutPayloadInstant = cancelled(UUID.randomUUID());
        ((Map<String, Object>) withoutPayloadInstant.get("payload")).remove("cancelledAt");
        for (int i = 1; i <= 2; i++) episodes.reservations.add(new UUID(230, i));

        deliver(withoutPayloadInstant, 201);

        assertThat(episodes.episodes.values()).hasSize(2).allSatisfy(e -> assertThat(e.cancelledAt()).isEqualTo(Instant.parse("2026-11-12T15:00:00Z")));
    }

    @Test void aFailureAnswersRetryLaterLeavesTheEventUnmarkedAndTheRedeliveryContinues() throws Exception {
        UUID unknown = new UUID(230, 3);
        alive.ids.add(unknown);
        UUID eventId = UUID.randomUUID();

        deliver(cancelled(eventId), 503);

        assertThat(events.processed).doesNotContain(eventId);
        episodes.reservations.add(unknown);
        deliver(cancelled(eventId), 200);
        assertThat(episodes.episodes).hasSize(3);
        assertThat(events.processed).contains(eventId);
    }

    @Test void waitsForTheScheduledEvent() throws Exception {
        departures.clear();

        deliver(cancelled(UUID.randomUUID()), 503);

        assertThat(episodes.episodes).isEmpty();
        assertThat(events.processed).isEmpty();
    }

    @Test void aLateCancellationOfAFinalizedDepartureOpensNothing() throws Exception {
        departures.save(new DepartureState(salida, TENANT, tour, Instant.parse("2027-03-01T13:00:00Z"), DepartureStatus.FINALIZED, 12, 2));

        deliver(cancelled(UUID.randomUUID()), 200);

        assertThat(episodes.episodes).isEmpty();
    }

    @Test void refusesATenantThatIsNotThePath() throws Exception {
        Map<String, Object> body = cancelled(UUID.randomUUID());
        body.put("tenantId", UUID.randomUUID().toString());

        deliver(body, 400);

        assertThat(episodes.episodes).isEmpty();
    }

    @Test void neverLogsPersonalData() throws Exception {
        deliver(cancelled(UUID.randomUUID()), 201);

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(line -> line.contains("Persona Ficticia") || line.contains("@example"));
    }
}
