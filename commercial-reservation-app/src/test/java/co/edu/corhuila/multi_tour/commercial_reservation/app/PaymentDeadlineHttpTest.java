package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-011 over HTTP, the public routes (api spec T-07, T-09; AC-11.3, AC-11.5 to AC-11.10): the pending list, the deadline, the
// follow-ups and the single extension. The user and the time are the token and the clock; every write needs its Idempotency-Key.
class PaymentDeadlineHttpTest extends ReservationHttpBase {

    private final UUID reservationId = new UUID(31, 1), ownReservationId = new UUID(31, 2);
    @Autowired PaymentDeadlineHttpFixture.MemoryDeadlines deadlines;

    @BeforeEach void plant() {
        deadlines.clear();
        plant(reservationId, customer);
        plant(ownReservationId, UUID.fromString(TestJwtKeys.ACCOUNT));
        payments.deadline = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.plus(Duration.ofHours(2)));
    }

    private void plant(UUID id, UUID owner) {
        store.reservations.put(id, Reservation.create(id, tenant, owner, departure, customer, 3,
                new ReservedService(tour, new Cents(100_000), new Cents(0)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofHours(2))));
    }

    private MockHttpServletRequestBuilder write(String path, Map<String, Object> body, String key, String role) throws Exception {
        var request = withToken(post(BASE + path), role).contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    @Test void theDeadlineIsReadWithItsEffectiveInstantByTheStaffAndByTheOwnerCustomerOnly() throws Exception {
        for (String role : List.of(ADMIN, COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST)) {
            var read = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + reservationId + "/payment-deadline"), role), 200));
            assertThat(read.get("kind").asText()).isEqualTo("INITIAL_PAYMENT");
            assertThat(read.get("dueAt").asText()).isEqualTo(NOW.plus(Duration.ofHours(2)).toString());
            assertThat(read.get("effectiveDeadline").asText()).isEqualTo(NOW.plus(Duration.ofHours(2)).toString());
            assertThat(read.get("departureStartsAt").asText()).isEqualTo(NOW.plusSeconds(86_400).toString());
            assertThat(read.get("frozen").asBoolean()).isFalse();
            assertThat(read.has("extensionHours")).isFalse();
        }
        exchange(withToken(get(BASE + "/reservations/" + ownReservationId + "/payment-deadline"), FINAL_CUSTOMER), 200);
        exchange(withToken(get(BASE + "/reservations/" + reservationId + "/payment-deadline"), FINAL_CUSTOMER), 404);
        exchange(withToken(get(BASE + "/reservations/" + new UUID(31, 99) + "/payment-deadline"), ADMIN), 404);
        payments.deadline = null;
        exchange(withToken(get(BASE + "/reservations/" + reservationId + "/payment-deadline"), ADMIN), 404);
    }

    @Test void aFollowupNeedsANoteAndAKeyIsStoredOnceWithTheTokenUserAndTheClockAndRepeatsWithTheStoredAnswer() throws Exception {
        String path = "/reservations/" + reservationId + "/payment-followups";
        CatalogHttpTest.assertField(exchange(write(path, Map.of("note", "Called"), null, COLLABORATOR), 400), "Idempotency-Key");
        CatalogHttpTest.assertField(exchange(write(path, Map.of("note", "  "), "followup-key-001", COLLABORATOR), 400), "note");

        var first = CatalogHttpTest.json(exchange(write(path, Map.of("note", "The customer will pay on Friday"), "followup-key-002", COLLABORATOR), 201));
        var again = CatalogHttpTest.json(exchange(write(path, Map.of("note", "The customer will pay on Friday"), "followup-key-002", COLLABORATOR), 200));

        assertThat(first.get("note").asText()).isEqualTo("The customer will pay on Friday");
        assertThat(first.get("contactedBy").asText()).isEqualTo(TestJwtKeys.ACCOUNT);
        assertThat(first.get("contactedAt").asText()).isEqualTo(NOW.toString());
        assertThat(again).isEqualTo(first);
        assertThat(deadlines.followups).hasSize(1);
        exchange(write(path, Map.of("note", "Another"), "followup-key-002", COLLABORATOR), 422);
        assertThat(payments.deadline.dueAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
    }

    @Test void theHistoryIsPaginatedNewestFirstAndOnlyTheWritingRolesWrite() throws Exception {
        String path = "/reservations/" + reservationId + "/payment-followups";
        exchange(write(path, Map.of("note", "First"), "followup-key-010", ADMIN), 201);
        exchange(write(path, Map.of("note", "Second"), "followup-key-011", ADMIN), 201);

        var page = CatalogHttpTest.json(exchange(withToken(get(BASE + path + "?limit=1"), ANALYST), 200));
        assertThat(page.at("/meta/total").asInt()).isEqualTo(2);
        assertThat(page.at("/data").size()).isEqualTo(1);
        for (String role : List.of(MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER)) {
            exchange(write(path, Map.of("note", "x"), "followup-key-0" + role.length() + role.charAt(0), role), 403);
        }
        exchange(withToken(get(BASE + path), FINAL_CUSTOMER), 403);
        exchange(write("/reservations/" + new UUID(31, 99) + "/payment-followups", Map.of("note", "x"), "followup-key-020", ADMIN), 404);
    }

    @Test void theSingleExtensionMovesTheDeadlineAndASecondOneIsRefused() throws Exception {
        String path = "/reservations/" + reservationId + "/payment-deadline/extension";
        CatalogHttpTest.assertField(exchange(write(path, Map.of("hours", 0), "extension-key-001", ADMIN), 400), "hours");
        exchange(write(path, Map.of("hours", 49), "extension-key-002", ADMIN), 422);

        var granted = CatalogHttpTest.json(exchange(write(path, Map.of("hours", 5), "extension-key-003", COLLABORATOR), 201));
        var again = CatalogHttpTest.json(exchange(write(path, Map.of("hours", 5), "extension-key-003", COLLABORATOR), 200));

        assertThat(granted.get("extensionHours").asInt()).isEqualTo(5);
        assertThat(granted.get("extensionGrantedBy").asText()).isEqualTo(TestJwtKeys.ACCOUNT);
        assertThat(granted.get("effectiveDeadline").asText()).isEqualTo(NOW.plus(Duration.ofHours(7)).toString());
        assertThat(again).isEqualTo(granted);
        var second = CatalogHttpTest.json(exchange(write(path, Map.of("hours", 1), "extension-key-004", ADMIN), 422));
        assertThat(second.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        exchange(write(path, Map.of("hours", 1), "extension-key-005", MANAGER), 403);
    }

    @Test void thePendingListIsForTheReadingRolesAndNamesNoUnknownParameter() throws Exception {
        for (String role : List.of(ADMIN, COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST)) {
            var page = CatalogHttpTest.json(exchange(withToken(get(BASE + "/pending-payments?kind=INITIAL_PAYMENT&page=1&limit=5"), role), 200));
            assertThat(page.at("/meta/total").asInt()).isZero();
        }
        assertThat(deadlines.asked).hasSize(5).allMatch(row -> row.endsWith(":INITIAL_PAYMENT:1:5"));
        exchange(withToken(get(BASE + "/pending-payments"), FINAL_CUSTOMER), 403);
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/pending-payments?kind=OTHER"), ADMIN), 400), "kind");
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/pending-payments?color=red"), ADMIN), 400), "color");
        exchange(get(BASE + "/pending-payments").header("Authorization", "Bearer " + CustomerHttpTest.token(null, "SERVICE", List.of()))
                .header("X-Correlation-Id", TRACE), 403);
    }

    @Test void theCreationWritesTheInitialDeadlineFromTheInitialWaitAndNoneWhenThereIsNoWait() throws Exception {
        var created = CatalogHttpTest.json(exchange(create(createBody(), "reservation-key-31", ADMIN), 201));
        assertThat(deadlines.initial).isEmpty();

        deadlines.initialWait = 24;
        var withWait = CatalogHttpTest.json(exchange(create(createBody(), "reservation-key-32", ADMIN), 201));

        assertThat(deadlines.initial).containsExactly(withWait.get("reservationId").asText() + ":" + NOW.plus(Duration.ofHours(24)));
        assertThat(created.get("reservationId").asText()).isNotEqualTo(withWait.get("reservationId").asText());
        exchange(create(createBody(), "reservation-key-32", ADMIN), 200);
        assertThat(deadlines.initial).hasSize(1);
    }
}
