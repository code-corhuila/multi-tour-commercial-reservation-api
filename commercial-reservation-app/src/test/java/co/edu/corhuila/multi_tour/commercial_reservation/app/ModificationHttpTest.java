package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Duration;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-012 over HTTP (api spec T-07 to T-09; AC-12.1 to AC-12.10, AC-12.15): the quote, the modification and the history. The user and the
// instant are the token and the clock; the role gate runs before the body is read; the write needs its Idempotency-Key.
@Import(ModificationHttpFixture.class)
class ModificationHttpTest extends ReservationHttpBase {

    private final UUID reservationId = new UUID(42, 1), otherTenantReservation = new UUID(42, 2);
    @Autowired ModificationHttpFixture.MemoryModifications modifications;

    @BeforeEach void plant() {
        modifications.clear();
        plant(reservationId, tenant);
        plant(otherTenantReservation, otherTenant);
    }

    private void plant(UUID id, UUID owner) {
        Reservation reservation = Reservation.create(id, owner, customer, departure, customer, 3,
                new ReservedService(tour, new Cents(100_000), new Cents(0)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofDays(2)));
        reservation.restoredAs(ReservationStatus.PENDING_PAYMENT, PaymentStatus.NO_PAYMENT, new Cents(300_000), false, List.of());
        store.reservations.put(id, reservation);
    }

    private MockHttpServletRequestBuilder modify(UUID id, Map<String, Object> body, String key, String role) throws Exception {
        var request = withToken(post(BASE + "/reservations/" + id + "/modifications"), role).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private MockHttpServletRequestBuilder quoteOf(UUID id, Map<String, Object> body, String role) throws Exception {
        return withToken(post(BASE + "/reservations/" + id + "/modification-quote"), role).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
    }

    private static List<String> names(List<PaymentEvent> events) {
        return events.stream().map(e -> e.getClass().getSimpleName()).toList();
    }

    @Test void aCollaboratorModifiesOnceWithTheTokenUserAndARepeatAnswersTheStoredRecord() throws Exception {
        var body = Map.<String, Object>of("changeType", "MODIFICATION", "people", 2, "reason", "One traveller less");

        var created = exchange(modify(reservationId, body, "modify-key-001", COLLABORATOR), 201);
        var first = CatalogHttpTest.json(created);
        var again = CatalogHttpTest.json(exchange(modify(reservationId, body, "modify-key-001", COLLABORATOR), 200));

        assertThat(created.getResponse().getHeader("Location")).endsWith("/reservations/" + reservationId + "/modifications");
        assertThat(first.get("changeType").asText()).isEqualTo("MODIFICATION");
        assertThat(first.get("people").asInt()).isEqualTo(2);
        assertThat(first.get("originalFinalValueCents").asLong()).isEqualTo(300_000);
        assertThat(first.get("newFinalValueCents").asLong()).isEqualTo(200_000);
        assertThat(first.get("resultingPendingBalanceCents").asLong()).isEqualTo(200_000);
        assertThat(first.get("seatDelta").asInt()).isEqualTo(-1);
        assertThat(first.get("excessDecisionOpened").asBoolean()).isFalse();
        assertThat(first.get("reservationStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(first.get("reason").asText()).isEqualTo("One traveller less");
        assertThat(first.get("modifiedBy").asText()).isEqualTo(TestJwtKeys.ACCOUNT);
        assertThat(first.get("modifiedAt").asText()).isEqualTo(NOW.toString());
        assertThat(again).isEqualTo(first);
        assertThat(store.reservations.get(reservationId).people()).isEqualTo(2);
        assertThat(modifications.rows).hasSize(1);
        assertThat(names(payments.events)).containsExactly("ReservationChanged");
    }

    @Test void theQuoteGivesTheNumbersAndTheRefusalWithoutWritingAnything() throws Exception {
        var quote = CatalogHttpTest.json(exchange(quoteOf(reservationId, Map.of("changeType", "MODIFICATION", "people", 2), ADMIN), 200));

        assertThat(quote.get("allowed").asBoolean()).isTrue();
        assertThat(quote.get("originalFinalValueCents").asLong()).isEqualTo(300_000);
        assertThat(quote.get("newFinalValueCents").asLong()).isEqualTo(200_000);
        assertThat(quote.get("differenceCents").asLong()).isEqualTo(-100_000);
        assertThat(quote.get("seatDelta").asInt()).isEqualTo(-1);
        assertThat(quote.get("seatsAvailable").asBoolean()).isTrue();
        modifications.policy = Optional.empty();
        var refused = CatalogHttpTest.json(exchange(quoteOf(reservationId, Map.of("changeType", "MODIFICATION", "people", 4), ADMIN), 200));
        assertThat(refused.get("allowed").asBoolean()).isFalse();
        assertThat(refused.get("refusalReason").asText()).isEqualTo("NO_POLICY");
        assertThat(store.reservations.get(reservationId).people()).isEqualTo(3);
        assertThat(modifications.rows).isEmpty();
        assertThat(payments.events).isEmpty();
    }

    @Test void theHistoryIsReadNewestFirstByTheStaffAndNotByTheCustomer() throws Exception {
        exchange(modify(reservationId, Map.of("changeType", "MODIFICATION", "people", 2, "reason", "First"), "modify-key-002", ADMIN), 201);
        exchange(modify(reservationId, Map.of("changeType", "MODIFICATION", "people", 1, "reason", "Second"), "modify-key-003", ADMIN), 201);

        var page = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + reservationId + "/modifications"), MANAGER), 200));

        assertThat(page.get("meta").get("total").asInt()).isEqualTo(2);
        assertThat(page.get("data").get(0).get("reason").asText()).isEqualTo("Second");
        exchange(withToken(get(BASE + "/reservations/" + reservationId + "/modifications"), FINAL_CUSTOMER), 403);
    }

    @Test void theCustomerManagerAccountantAndAnalystAreRefusedBeforeTheBodyIsReadAndNothingChanges() throws Exception {
        for (String role : List.of(FINAL_CUSTOMER, MANAGER, ACCOUNTANT, ANALYST)) {
            exchange(modify(reservationId, Map.of("reason", ""), "modify-key-004", role), 403);
            exchange(quoteOf(reservationId, Map.of("changeType", "MODIFICATION", "people", 4), role), 403);
        }
        assertThat(store.reservations.get(reservationId).people()).isEqualTo(3);
        assertThat(modifications.rows).isEmpty();
    }

    @Test void everyInvalidFieldIsNamedAndAWriteNeedsItsKeyAndAnotherTenantLooksMissing() throws Exception {
        exchange(modify(reservationId, Map.of("changeType", "MODIFICATION", "people", 4, "reason", "No key"), null, ADMIN), 400);
        var invalid = CatalogHttpTest.json(exchange(modify(reservationId, Map.of("changeType", "SOMETHING", "reason", ""), "modify-key-005", ADMIN), 400));
        assertThat(invalid.toString()).contains("changeType").contains("reason");
        exchange(modify(reservationId, Map.of("changeType", "MODIFICATION", "reason", "Nothing to change"), "modify-key-006", ADMIN), 400);
        exchange(modify(reservationId, Map.of("changeType", "MODIFICATION", "people", 4, "reason", "Not mine", "tenantId", tenant.toString()),
                "modify-key-007", ADMIN), 400);
        exchange(modify(otherTenantReservation, Map.of("changeType", "MODIFICATION", "people", 4, "reason", "Not mine"), "modify-key-008", ADMIN), 404);
        assertThat(store.reservations.get(reservationId).people()).isEqualTo(3);
    }

    @Test void theTermsRefuseWithTheReasonAndAKeyUsedOnAnotherRequestIsARuleViolation() throws Exception {
        var body = Map.<String, Object>of("changeType", "MODIFICATION", "people", 2, "reason", "Asked");
        modifications.policy = Optional.empty();
        var refused = CatalogHttpTest.json(exchange(modify(reservationId, body, "modify-key-009", ADMIN), 422));
        assertThat(refused.toString()).contains("NO_POLICY");
        modifications.policy = Optional.of(new co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangePolicy(true, 60));
        exchange(modify(reservationId, body, "modify-key-010", ADMIN), 201);
        exchange(modify(reservationId, Map.of("changeType", "MODIFICATION", "people", 1, "reason", "Other"), "modify-key-010", ADMIN), 422);
        assertThat(modifications.rows).hasSize(1);
    }
}
