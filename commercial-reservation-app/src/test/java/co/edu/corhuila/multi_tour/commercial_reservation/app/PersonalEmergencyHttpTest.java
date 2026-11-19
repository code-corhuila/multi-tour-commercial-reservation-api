package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundBasis;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Spec 024 (-api), T-02 and T-03: PersonalEmergencyRecorded over the route receiveEvent. The payload is the one operations-cost writes (aggregateId is the departure,
// `affected`, never the detail). Over memory fakes; the atomicity of the refund and the mark has its own use case test. Every value is invented.
@Import({RefundHttpFixture.class, TenantEventHttpFixture.class, PersonalEmergencyHttpTest.Policy.class})
class PersonalEmergencyHttpTest extends ReservationHttpBase {

    @TestConfiguration
    static class Policy {
        static final class Rows implements CancellationPolicyReader {
            volatile OptionalInt basisPoints = OptionalInt.empty();
            @Override public OptionalInt penaltyBasisPoints(UUID tenantId, UUID serviceId, Instant departureStart, Instant now) { return basisPoints; }
        }
        @Bean @Primary Rows policyRows() { return new Rows(); }
    }

    private final UUID salida = new UUID(24, 10), reservationId = new UUID(24, 11), service = new UUID(24, 12), operator = new UUID(24, 13);
    @Autowired RefundHttpFixture.MemoryRefunds refunds;
    @Autowired RefundHttpFixture.MemorySnapshots bases;
    @Autowired TenantEventHttpFixture.MemoryTenants processed;
    @Autowired Policy.Rows policy;
    private String serviceToken;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach void plant() throws Exception {
        refunds.clear();
        bases.snapshots.clear();
        processed.clear();
        policy.basisPoints = OptionalInt.of(2_500);
        serviceToken = CustomerHttpTest.token(null, "SERVICE", List.of());
        Reservation reservation = Reservation.create(reservationId, tenant, customer, salida, operator, 3, new ReservedService(service, new Cents(100_000), new Cents(50_000)),
                new Cents(450_000), new Cents(450_000), List.of(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW.minusSeconds(86_400L * 30));
        reservation.restoredAs(ReservationStatus.IN_EXECUTION, PaymentStatus.FULLY_PAID, new Cents(0), false, List.of());
        store.save(reservation);
        bases.snapshots.put(reservationId, new RefundHttpFixture.MemorySnapshots.Planted(tenant, new RefundBasis(reservationId, new Cents(300_000), 3, service, null)));
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    @AfterEach void release() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }

    private Map<String, Object> emergency(UUID eventId, Object affected) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenant.toString());
        payload.put("salidaId", salida.toString());
        payload.put("reservationId", reservationId.toString());
        if (affected != null) payload.put("affected", affected);
        payload.put("recordedBy", operator.toString());
        payload.put("recordedAt", "2026-11-12T15:00:00Z");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId.toString());
        body.put("eventType", "PersonalEmergencyRecorded");
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

    @Test void determinesTheRefundOnceAndARedeliveryAnswersOk() throws Exception {
        UUID eventId = UUID.randomUUID();

        exchange(deliver(emergency(eventId, 1), serviceToken), 201);
        exchange(deliver(emergency(eventId, 1), serviceToken), 200);

        assertThat(refunds.refunds).hasSize(1);
        var refund = refunds.refunds.values().iterator().next();
        assertThat(refund.origin()).isEqualTo(RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION);
        // share 300000 * 1 / 3 = 100000, penalty 25 percent = 25000
        assertThat(refund.authorizedAmount()).isEqualTo(new Cents(75_000));
        assertThat(refunds.events).hasSize(1);
        assertThat(store.reservations.get(reservationId).reservationStatus()).isEqualTo(ReservationStatus.IN_EXECUTION);
    }

    @Test void aPolicyThatKeepsEverythingCreatesNoRefundAndStillAnswersCreated() throws Exception {
        policy.basisPoints = OptionalInt.empty();
        UUID eventId = UUID.randomUUID();

        exchange(deliver(emergency(eventId, 1), serviceToken), 201);

        assertThat(refunds.refunds).isEmpty();
        assertThat(processed.processed).contains(eventId);
    }

    @Test void aSecondEventForTheSameReservationAnswersOkAndCreatesNothingMore() throws Exception {
        exchange(deliver(emergency(UUID.randomUUID(), 1), serviceToken), 201);
        exchange(deliver(emergency(UUID.randomUUID(), 2), serviceToken), 200);

        assertThat(refunds.refunds).hasSize(1);
    }

    @Test void refusesAnInvalidAffectedNumber() throws Exception {
        CatalogHttpTest.assertField(exchange(deliver(emergency(UUID.randomUUID(), null), serviceToken), 400), "affected");
        CatalogHttpTest.assertField(exchange(deliver(emergency(UUID.randomUUID(), 0), serviceToken), 400), "affected");
        CatalogHttpTest.assertField(exchange(deliver(emergency(UUID.randomUUID(), 4), serviceToken), 400), "affected");
        assertThat(refunds.refunds).isEmpty();
        assertThat(processed.processed).isEmpty();
    }

    @Test void refusesATenantThatIsNotThePath() throws Exception {
        Map<String, Object> body = emergency(UUID.randomUUID(), 1);
        body.put("tenantId", otherTenant.toString());

        CatalogHttpTest.assertField(exchange(deliver(body, serviceToken), 400), "tenantId");
        assertThat(refunds.refunds).isEmpty();
    }

    @Test void leavesAnUnknownReservationUnmarked() throws Exception {
        store.reservations.clear();
        UUID eventId = UUID.randomUUID();

        CatalogHttpTest.assertField(exchange(deliver(emergency(eventId, 1), serviceToken), 400), "reservationId");

        assertThat(processed.processed).doesNotContain(eventId);
    }

    @Test void ignoresACancelledReservation() throws Exception {
        store.reservations.get(reservationId).cancel(co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER, null, NOW, 0);

        exchange(deliver(emergency(UUID.randomUUID(), 1), serviceToken), 200);

        assertThat(refunds.refunds).isEmpty();
    }

    @Test void needsTheServiceRoleAndTheEventIdAsKey() throws Exception {
        exchange(deliver(emergency(UUID.randomUUID(), 1), token(ADMIN)), 403);
        var wrongKey = post(BASE + "/internal/tenants/" + tenant + "/events").header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(emergency(UUID.randomUUID(), 1))).header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + serviceToken);
        CatalogHttpTest.assertField(exchange(wrongKey, 400), "Idempotency-Key");
    }

    @Test void neverLogsPersonalData() throws Exception {
        exchange(deliver(emergency(UUID.randomUUID(), 1), serviceToken), 201);
        exchange(deliver(emergency(UUID.randomUUID(), 9), serviceToken), 400);

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(line -> line.contains(operator.toString()) || line.contains("Persona Ficticia"));
    }
}
