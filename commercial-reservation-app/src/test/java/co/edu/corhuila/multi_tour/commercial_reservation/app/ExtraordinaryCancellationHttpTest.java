package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Spec 025 (-api), T-02: ExtraordinaryCancellationRecorded over the route receiveEvent. The payload is the one operations-cost writes (aggregateId is the departure; the justification is
// free text of 1 to 500 characters that is read only to check its length). Over memory fakes. Every value is invented.
@Import({TenantEventHttpFixture.class, ReschedulingHttpFixture.class})
class ExtraordinaryCancellationHttpTest extends ReservationHttpBase {
    private static final String TEXT = "TEXTO-LIBRE-FICTICIO-QUE-NO-DEBE-VIAJAR";
    private final UUID salida = new UUID(25, 10), reservationId = new UUID(25, 11), service = new UUID(25, 12), operator = new UUID(25, 13);
    @Autowired ReschedulingHttpFixture.MemoryEpisodes episodes;
    @Autowired TenantEventHttpFixture.MemoryTenants processed;
    private String serviceToken;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach void plant() throws Exception {
        episodes.clear();
        processed.clear();
        episodes.reservations.add(reservationId);
        serviceToken = CustomerHttpTest.token(null, "SERVICE", List.of());
        Reservation reservation = Reservation.create(reservationId, tenant, customer, salida, operator, 3, new ReservedService(service, new Cents(100_000), new Cents(50_000)),
                new Cents(450_000), new Cents(450_000), List.of(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW.minusSeconds(86_400L * 30));
        reservation.restoredAs(ReservationStatus.IN_EXECUTION, PaymentStatus.FULLY_PAID, new Cents(0), false, List.of());
        store.save(reservation);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    @AfterEach void release() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }

    private Map<String, Object> cancellation(UUID eventId, Object justification) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenant.toString());
        payload.put("salidaId", salida.toString());
        payload.put("reservationId", reservationId.toString());
        if (justification != null) payload.put("justification", justification);
        payload.put("recordedBy", operator.toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId.toString());
        body.put("eventType", "ExtraordinaryCancellationRecorded");
        body.put("eventVersion", 1);
        body.put("tenantId", tenant.toString());
        body.put("aggregateId", salida.toString());
        body.put("payload", payload);
        body.put("occurredAt", "2026-11-12T15:00:00Z");
        return body;
    }

    private MockHttpServletRequestBuilder deliver(Map<String, Object> body, String bearer) throws Exception {
        var request = post(BASE + "/internal/tenants/" + tenant + "/events").header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body)).header("Idempotency-Key", body.get("eventId").toString());
        return bearer == null ? request : request.header("Authorization", "Bearer " + bearer);
    }

    @Test void opensOneEpisodeCountedFromTheEventAndKeepsTheReservationAlive() throws Exception {
        UUID eventId = UUID.randomUUID();

        exchange(deliver(cancellation(eventId, TEXT), serviceToken), 201);
        exchange(deliver(cancellation(eventId, TEXT), serviceToken), 200);

        assertThat(episodes.episodes).hasSize(1);
        var episode = episodes.episodes.values().iterator().next();
        assertThat(episode.originalSalidaId()).isEqualTo(salida);
        assertThat(episode.cancelledAt()).isEqualTo(Instant.parse("2026-11-12T15:00:00Z"));
        assertThat(store.reservations.get(reservationId).reservationStatus()).isEqualTo(ReservationStatus.IN_EXECUTION);
    }

    @Test void aSecondEventForTheSameReservationAnswersOkAndOpensNothingMore() throws Exception {
        exchange(deliver(cancellation(UUID.randomUUID(), TEXT), serviceToken), 201);
        exchange(deliver(cancellation(UUID.randomUUID(), TEXT), serviceToken), 200);

        assertThat(episodes.episodes).hasSize(1);
    }

    @Test void refusesAJustificationOutsideOneToFiveHundredCharacters() throws Exception {
        CatalogHttpTest.assertField(exchange(deliver(cancellation(UUID.randomUUID(), null), serviceToken), 400), "justification");
        CatalogHttpTest.assertField(exchange(deliver(cancellation(UUID.randomUUID(), ""), serviceToken), 400), "justification");
        CatalogHttpTest.assertField(exchange(deliver(cancellation(UUID.randomUUID(), "x".repeat(501)), serviceToken), 400), "justification");
        assertThat(episodes.episodes).isEmpty();
        assertThat(processed.processed).isEmpty();
    }

    @Test void refusesAReservationOfAnotherDepartureAndLeavesTheEventUnmarked() throws Exception {
        Map<String, Object> body = cancellation(UUID.randomUUID(), TEXT);
        UUID elsewhere = new UUID(25, 99);
        ((Map<String, Object>) body.get("payload")).put("salidaId", elsewhere.toString());
        body.put("aggregateId", elsewhere.toString());

        CatalogHttpTest.assertField(exchange(deliver(body, serviceToken), 400), "reservationId");

        assertThat(episodes.episodes).isEmpty();
        assertThat(processed.processed).doesNotContain(UUID.fromString(body.get("eventId").toString()));
    }

    @Test void refusesATenantThatIsNotThePath() throws Exception {
        Map<String, Object> body = cancellation(UUID.randomUUID(), TEXT);
        body.put("tenantId", otherTenant.toString());

        CatalogHttpTest.assertField(exchange(deliver(body, serviceToken), 400), "tenantId");
    }

    @Test void ignoresACancelledReservation() throws Exception {
        store.reservations.get(reservationId).cancel(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER, null, NOW, 0);

        exchange(deliver(cancellation(UUID.randomUUID(), TEXT), serviceToken), 200);

        assertThat(episodes.episodes).isEmpty();
    }

    @Test void opensTheEpisodeForAnInactiveTenant() throws Exception {
        directory.tenants.put(tenant.toString(), new TenantRef(tenant, false));

        exchange(deliver(cancellation(UUID.randomUUID(), TEXT), serviceToken), 201);

        assertThat(episodes.episodes).hasSize(1);
    }

    @Test void needsTheServiceRole() throws Exception {
        exchange(deliver(cancellation(UUID.randomUUID(), TEXT), token(ADMIN)), 403);
    }

    @Test void neverLogsTheJustification() throws Exception {
        exchange(deliver(cancellation(UUID.randomUUID(), TEXT), serviceToken), 201);
        exchange(deliver(cancellation(UUID.randomUUID(), TEXT + "x".repeat(500)), serviceToken), 400);

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(line -> line.contains(TEXT) || line.contains(operator.toString()));
        assertThat(episodes.episodes.values().toString()).doesNotContain(TEXT);
    }
}
