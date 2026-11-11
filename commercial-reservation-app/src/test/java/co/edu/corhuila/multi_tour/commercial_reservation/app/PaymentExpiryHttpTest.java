package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-011 over HTTP, the internal routes of job 1 (api spec T-05, T-07; AC-11.2, AC-11.9, AC-11.12, AC-11.13): the due list and the
// expiry, for the service token only, with the tenant of the path. The job asks and the service decides.
class PaymentExpiryHttpTest extends ReservationHttpBase {

    private final UUID reservationId = new UUID(32, 1);
    @Autowired PaymentDeadlineHttpFixture.MemoryDeadlines deadlines;
    private String service;

    @BeforeEach void plant() throws Exception {
        deadlines.clear();
        service = CustomerHttpTest.token(null, "SERVICE", List.of());
        store.reservations.put(reservationId, Reservation.create(reservationId, tenant, customer, departure, customer, 3,
                new ReservedService(tour, new Cents(100_000), new Cents(0)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofHours(30))));
        payments.deadline = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.minus(Duration.ofHours(1)));
    }

    private MockHttpServletRequestBuilder expiry(UUID forTenant, String key, String bearer) {
        var request = post(BASE + "/internal/tenants/" + forTenant + "/reservations/" + reservationId + "/expiry")
                .header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", TRACE);
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private static List<String> names(List<PaymentEvent> events) {
        return events.stream().map(e -> e.getClass().getSimpleName()).toList();
    }

    @Test void theServiceTokenExpiresAnUnpaidReservationOnceAndARepeatWithTheSameKeyAnswersTheSame() throws Exception {
        var first = CatalogHttpTest.json(exchange(expiry(tenant, "expiry-key-001", service), 200));
        var again = CatalogHttpTest.json(exchange(expiry(tenant, "expiry-key-001", service), 200));

        assertThat(first.get("expired").asBoolean()).isTrue();
        assertThat(first.get("cause").asText()).isEqualTo("PAYMENT_EXPIRY");
        assertThat(first.get("reservationStatus").asText()).isEqualTo("CANCELLED");
        assertThat(first.has("reason")).isFalse();
        assertThat(again).isEqualTo(first);
        assertThat(store.reservations.get(reservationId).reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(names(payments.events)).containsExactly("ReservationCancelled");
    }

    @Test void aReservationThatIsNotDueOrIsPaidIsNotExpiredAndNothingChanges() throws Exception {
        payments.deadline = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.plus(Duration.ofHours(1)));
        var notDue = CatalogHttpTest.json(exchange(expiry(tenant, "expiry-key-002", service), 200));
        assertThat(notDue.get("expired").asBoolean()).isFalse();
        assertThat(notDue.get("reason").asText()).isEqualTo("NOT_DUE");
        assertThat(notDue.get("reservationStatus").asText()).isEqualTo("PENDING_PAYMENT");

        payments.deadline = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.minus(Duration.ofHours(1)));
        store.reservations.get(reservationId).registerTransfer(new UUID(32, 9), new Cents(90_000), new Cents(90_000), customer, NOW.minus(Duration.ofHours(2)));
        var proof = CatalogHttpTest.json(exchange(expiry(tenant, "expiry-key-003", service), 200));
        assertThat(proof.get("reason").asText()).isEqualTo("PROOF_IN_VALIDATION");
        assertThat(payments.events).isEmpty();
        assertThat(store.saves).isZero();
    }

    @Test void theExpiryIsForTheServiceTokenOnlyNeedsItsKeyAndAnotherTenantLooksMissing() throws Exception {
        exchange(expiry(tenant, "expiry-key-004", token(ADMIN)), 403);
        CatalogHttpTest.assertField(exchange(expiry(tenant, null, service), 400), "Idempotency-Key");
        exchange(expiry(otherTenant, "expiry-key-005", service), 404);
        exchange(expiry(tenant, "expiry-key-006", token(ADMIN)), 403);
        assertThat(store.reservations.get(reservationId).reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test void theDueListIsForTheServiceTokenWithTheTenantOfThePath() throws Exception {
        deadlines.due.add(new DeadlineView(reservationId, payments.deadline, NOW.plusSeconds(86_400), NOW.minus(Duration.ofHours(1))));
        MockHttpServletRequestBuilder list = get(BASE + "/internal/tenants/" + tenant + "/payment-deadlines?due=true&limit=10")
                .header("Authorization", "Bearer " + service).header("X-Correlation-Id", TRACE);

        var page = CatalogHttpTest.json(exchange(list, 200));

        assertThat(page.at("/meta/total").asInt()).isEqualTo(1);
        assertThat(page.at("/data/0/reservationId").asText()).isEqualTo(reservationId.toString());
        assertThat(page.at("/data/0/kind").asText()).isEqualTo("INITIAL_PAYMENT");
        assertThat(page.at("/data/0/effectiveDeadline").asText()).isEqualTo(NOW.minus(Duration.ofHours(1)).toString());
        assertThat(deadlines.asked).singleElement().asString().startsWith("due:" + tenant + ":" + NOW + ":1:10");
        exchange(get(BASE + "/internal/tenants/" + tenant + "/payment-deadlines?due=true").header("Authorization", "Bearer " + token(ADMIN))
                .header("X-Correlation-Id", TRACE), 403);
        CatalogHttpTest.assertField(exchange(get(BASE + "/internal/tenants/" + tenant + "/payment-deadlines")
                .header("Authorization", "Bearer " + service).header("X-Correlation-Id", TRACE), 400), "due");
    }
}
