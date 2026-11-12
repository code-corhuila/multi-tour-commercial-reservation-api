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
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-013 over HTTP (api spec T-05, T-06; AC-13.1 to AC-13.10, AC-13.12): the quote, the cancellation and the record. The user and the
// instant are the token and the clock; the role gate runs before the body is read; every write needs its Idempotency-Key.
@Import(CancellationHttpFixture.class)
@ExtendWith(OutputCaptureExtension.class)
class CancellationHttpTest extends ReservationHttpBase {

    private final UUID reservationId = new UUID(41, 1), otherTenantReservation = new UUID(41, 2);
    @Autowired CancellationHttpFixture.MemoryCancellations cancellations;

    @BeforeEach void plant() {
        cancellations.clear();
        plant(reservationId, tenant, ReservationStatus.CONFIRMED);
    }

    private void plant(UUID id, UUID owner, ReservationStatus status) {
        Reservation reservation = Reservation.create(id, owner, customer, departure, customer, 3,
                new ReservedService(tour, new Cents(100_000), new Cents(0)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofDays(2)));
        reservation.restoredAs(status, PaymentStatus.FULLY_PAID, new Cents(0), false, List.of());
        store.reservations.put(id, reservation);
    }

    private static List<String> names(List<PaymentEvent> events) {
        return events.stream().map(e -> e.getClass().getSimpleName()).toList();
    }

    private MockHttpServletRequestBuilder cancel(UUID id, Map<String, Object> body, String key, String role) throws Exception {
        var request = withToken(post(BASE + "/reservations/" + id + "/cancellation"), role).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private static Map<String, Object> body(String reason) {
        return Map.of("reason", reason, "origin", "CUSTOMER_REQUEST");
    }

    @Test void aCollaboratorCancelsOnceWithTheTokenUserAndTheClockAndARepeatAnswersTheStoredRecord() throws Exception {
        cancellations.basisPoints = OptionalInt.of(5_000);

        var created = exchange(cancel(reservationId, body("The customer cannot travel"), "cancel-key-001", COLLABORATOR), 201);
        var first = CatalogHttpTest.json(created);
        var again = CatalogHttpTest.json(exchange(cancel(reservationId, body("The customer cannot travel"), "cancel-key-001", COLLABORATOR), 200));

        assertThat(created.getResponse().getHeader("Location")).endsWith("/reservations/" + reservationId + "/cancellation");
        assertThat(first.get("reservationStatus").asText()).isEqualTo("CANCELLED");
        assertThat(first.get("reason").asText()).isEqualTo("The customer cannot travel");
        assertThat(first.get("origin").asText()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(first.get("validatedValueCents").asLong()).isEqualTo(300_000);
        assertThat(first.get("penaltyBasisPoints").asInt()).isEqualTo(5_000);
        assertThat(first.get("penaltyCents").asLong()).isEqualTo(150_000);
        assertThat(first.get("refundableCents").asLong()).isEqualTo(150_000);
        assertThat(first.get("refundDecisionOpened").asBoolean()).isTrue();
        assertThat(first.get("cancelledBy").asText()).isEqualTo(TestJwtKeys.ACCOUNT);
        assertThat(first.get("cancelledAt").asText()).isEqualTo(NOW.toString());
        assertThat(again).isEqualTo(first);
        assertThat(store.reservations.get(reservationId).reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(names(payments.events)).containsExactly("ReservationCancelled");
        assertThat(cancellations.opened).containsExactly("300000/150000");
    }

    @Test void theQuoteGivesTheAmountsAndTheRefusalWithoutWritingAnything() throws Exception {
        cancellations.basisPoints = OptionalInt.of(5_000);

        var quote = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + reservationId + "/cancellation-quote"), ADMIN), 200));

        assertThat(quote.get("cancellable").asBoolean()).isTrue();
        assertThat(quote.get("validatedValueCents").asLong()).isEqualTo(300_000);
        assertThat(quote.get("penaltyCents").asLong()).isEqualTo(150_000);
        assertThat(quote.get("refundableCents").asLong()).isEqualTo(150_000);
        assertThat(quote.has("refusalReason")).isFalse();
        cancellations.marked.add(reservationId);
        var marked = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + reservationId + "/cancellation-quote"), ADMIN), 200));
        assertThat(marked.get("cancellable").asBoolean()).isFalse();
        assertThat(marked.get("refusalReason").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(store.reservations.get(reservationId).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(payments.events).isEmpty();
        assertThat(cancellations.records).isEmpty();
    }

    @Test void theCustomerManagerAccountantAndAnalystAreRefusedBeforeTheBodyIsReadAndNothingChanges() throws Exception {
        for (String role : List.of(FINAL_CUSTOMER, MANAGER, ACCOUNTANT, ANALYST)) {
            exchange(cancel(reservationId, Map.of("reason", ""), "cancel-key-002", role), 403);
            exchange(withToken(get(BASE + "/reservations/" + reservationId + "/cancellation-quote"), role), 403);
        }
        assertThat(store.reservations.get(reservationId).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(cancellations.records).isEmpty();
    }

    @Test void anEmptyReasonAForeignOriginOrAMissingKeyIsRefusedNamingTheField() throws Exception {
        CatalogHttpTest.assertField(exchange(cancel(reservationId, body("Because"), null, COLLABORATOR), 400), "Idempotency-Key");
        CatalogHttpTest.assertField(exchange(cancel(reservationId, body("  "), "cancel-key-003", COLLABORATOR), 400), "reason");
        CatalogHttpTest.assertField(exchange(cancel(reservationId, Map.of("reason", "Because", "origin", "PAYMENT_EXPIRY"), "cancel-key-004",
                COLLABORATOR), 400), "origin");
        assertThat(store.reservations.get(reservationId).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test void aReservationThatCannotBeCancelledOrIsMarkedAnswers422AndAnotherTenantsOneAnswers404() throws Exception {
        var inExecution = new UUID(41, 3);
        plant(inExecution, tenant, ReservationStatus.IN_EXECUTION);
        var refused = CatalogHttpTest.json(exchange(cancel(inExecution, body("Because"), "cancel-key-005", ADMIN), 422));
        assertThat(refused.get("error").asText()).isEqualTo("INVALID_STATUS_TRANSITION");

        cancellations.marked.add(reservationId);
        var marked = CatalogHttpTest.json(exchange(cancel(reservationId, body("Because"), "cancel-key-006", ADMIN), 422));
        assertThat(marked.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(marked.get("message").asText()).contains("reject");

        plant(otherTenantReservation, otherTenant, ReservationStatus.CONFIRMED);
        exchange(cancel(otherTenantReservation, body("Because"), "cancel-key-007", ADMIN), 404);
        assertThat(store.reservations.get(reservationId).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(payments.events).isEmpty();
    }

    @Test void aKeyUsedByAnotherRequestIsARuleViolationNeverAReplay() throws Exception {
        exchange(cancel(reservationId, body("The customer cannot travel"), "cancel-key-008", ADMIN), 201);
        var other = new UUID(41, 4);
        plant(other, tenant, ReservationStatus.CONFIRMED);

        var reused = CatalogHttpTest.json(exchange(cancel(other, body("The customer cannot travel"), "cancel-key-008", ADMIN), 422));

        assertThat(reused.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(store.reservations.get(other).reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test void theRecordIsReadWholeByTheStaffRolesAndAnUncancelledReservationHasNone() throws Exception {
        exchange(withToken(get(BASE + "/reservations/" + reservationId + "/cancellation"), ANALYST), 404);
        exchange(cancel(reservationId, body("The customer cannot travel"), "cancel-key-009", COLLABORATOR), 201);

        for (String role : List.of(ADMIN, COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST)) {
            var record = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + reservationId + "/cancellation"), role), 200));
            assertThat(record.get("reason").asText()).isEqualTo("The customer cannot travel");
            assertThat(record.get("refundableCents").asLong()).isEqualTo(300_000);
            assertThat(record.get("refundDecisionOpened").asBoolean()).isTrue();
        }
        exchange(withToken(get(BASE + "/reservations/" + reservationId + "/cancellation"), FINAL_CUSTOMER), 403);
    }

    // AC-13.12 (RN-12, RNF-03): the event is written once, a repeat of the key writes none, and no log line of the three routes carries
    // the reason, a name, a document, an e-mail or a phone (the spec named this scan; before it only the event count was proved).
    @Test void theEventsAndTheLogsCarryNoPersonalDataNorTheReason(CapturedOutput output) throws Exception {
        String reason = "Motivo reservado ficticio 7731";
        cancellations.basisPoints = OptionalInt.of(5_000);

        exchange(withToken(get(BASE + "/reservations/" + reservationId + "/cancellation-quote"), ADMIN), 200);
        exchange(cancel(reservationId, body(reason), "cancel-key-010", COLLABORATOR), 201);
        exchange(cancel(reservationId, body(reason), "cancel-key-010", COLLABORATOR), 200);
        exchange(withToken(get(BASE + "/reservations/" + reservationId + "/cancellation"), ADMIN), 200);

        assertThat(names(payments.events)).containsExactly("ReservationCancelled");
        assertThat(payments.events.toString()).doesNotContain(reason, "Persona Ficticia", "persona@example.invalid", "CC12345", "3000000000");
        assertThat(output.getAll()).doesNotContain(reason, "Persona Ficticia", "persona@example.invalid", "CC12345", "3000000000");
    }
}
