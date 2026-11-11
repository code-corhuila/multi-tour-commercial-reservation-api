package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-010 over HTTP, the decisions (api spec T-03, T-04, T-06; AC-10.1 to AC-10.3, AC-10.8, AC-10.10, AC-10.13) and the step 2 of the
// saga register-payment that records the validated payment (DP-11, answered 2026-10-30; PROPOSED internal route).
class PaymentProofDecisionHttpTest extends ReservationHttpBase {

    private final UUID proof = new UUID(21, 1), reservationId = new UUID(21, 2);

    @BeforeEach void plant() {
        Reservation reservation = Reservation.create(reservationId, tenant, customer, departure, customer, 3,
                new ReservedService(tour, new Cents(100_000), new Cents(0)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofHours(2)));
        reservation.registerTransfer(proof, new Cents(90_000), new Cents(90_000), customer, NOW.minus(Duration.ofHours(2)));
        reservation.allocateSeat(NOW.minus(Duration.ofHours(2)));
        reservation.pullEvents();
        store.reservations.put(reservationId, reservation);
    }

    private MockHttpServletRequestBuilder decide(String action, Map<String, Object> body, String key, String bearer) throws Exception {
        var request = post(BASE + "/payment-proofs/" + proof + "/" + action).header("Authorization", "Bearer " + bearer)
                .header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private MockHttpServletRequestBuilder step2(UUID forTenant, Map<String, Object> body, String bearer) throws Exception {
        return post(BASE + "/internal/tenants/" + forTenant + "/payment-proofs/" + proof + "/validated-payment")
                .header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
    }

    private static List<String> names(List<PaymentEvent> events) {
        return events.stream().map(e -> e.getClass().getSimpleName()).toList();
    }

    @Test void theAdministratorApprovesTheProofAndTheReservationIsConfirmedWithTheStoredDecisionOnARepeat() throws Exception {
        var decision = CatalogHttpTest.json(exchange(decide("approve", Map.of("verifiedAmountCents", 90_000L), "decision-key-001", token(ADMIN)), 200));

        assertThat(decision.get("validationStatus").asText()).isEqualTo("APPROVED");
        assertThat(decision.get("appliedAmountCents").asLong()).isEqualTo(90_000L);
        assertThat(decision.get("decidedBy").asText()).isEqualTo(TestJwtKeys.ACCOUNT);
        assertThat(decision.get("reservationStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(decision.get("validatedValueCents").asLong()).isEqualTo(90_000L);
        assertThat(names(payments.events)).containsExactly("PaymentValidated", "ReservationConfirmed");

        var again = CatalogHttpTest.json(exchange(decide("approve", Map.of("verifiedAmountCents", 90_000L), "decision-key-001", token(ADMIN)), 200));
        assertThat(again).isEqualTo(decision);
        assertThat(payments.events).hasSize(2);
        var other = CatalogHttpTest.json(exchange(decide("approve", Map.of("verifiedAmountCents", 90_000L), "decision-key-002", token(ADMIN)), 422));
        assertThat(other.get("error").asText()).isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(payments.events).hasSize(2);
    }

    @Test void aDifferentVerifiedAmountNeedsAReasonAndTheExcessIsHandedToTheCreditBalance() throws Exception {
        CatalogHttpTest.assertField(exchange(decide("approve", Map.of("verifiedAmountCents", 80_000L), "decision-key-003", token(ADMIN)), 400), "reason");
        CatalogHttpTest.assertField(exchange(decide("approve", Map.of(), "decision-key-004", token(ADMIN)), 400), "verifiedAmountCents");
        CatalogHttpTest.assertField(exchange(decide("approve", Map.of("verifiedAmountCents", 0L), "decision-key-005", token(ADMIN)), 400), "verifiedAmountCents");
        CatalogHttpTest.assertField(exchange(decide("approve", Map.of("verifiedAmountCents", 90_000L), null, token(ADMIN)), 400), "Idempotency-Key");
        assertThat(payments.events).isEmpty();

        var body = new LinkedHashMap<String, Object>();
        body.put("verifiedAmountCents", 350_000L); body.put("reason", "The customer paid the whole value");
        var decision = CatalogHttpTest.json(exchange(decide("approve", body, "decision-key-006", token(ADMIN)), 200));

        assertThat(decision.get("appliedAmountCents").asLong()).isEqualTo(300_000L);
        assertThat(decision.get("excessAmountCents").asLong()).isEqualTo(50_000L);
        assertThat(payments.income).contains("excess:50000");
    }

    @Test void onlyWhoHoldsThePermissionDecidesAndNothingChangesForTheOthers() throws Exception {
        for (String role : List.of(COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER)) {
            exchange(decide("approve", Map.of("verifiedAmountCents", 90_000L), "decision-key-007", token(role)), 403);
            exchange(decide("reject", Map.of("reason", "illegible"), "decision-key-008", token(role)), 403);
        }
        assertThat(payments.events).isEmpty();
        String granted = CustomerHttpTest.token(tenant.toString(), COLLABORATOR, List.of("VALIDATE_PAYMENT_PROOFS"));
        exchange(decide("approve", Map.of("verifiedAmountCents", 90_000L), "decision-key-009", granted), 200);
    }

    @Test void aRejectionKeepsTheReasonAndTheDecisionIsFinal() throws Exception {
        CatalogHttpTest.assertField(exchange(decide("reject", Map.of(), "decision-key-010", token(ADMIN)), 400), "reason");

        var decision = CatalogHttpTest.json(exchange(decide("reject", Map.of("reason", "The proof is illegible"), "decision-key-011", token(ADMIN)), 200));

        assertThat(decision.get("validationStatus").asText()).isEqualTo("REJECTED");
        assertThat(decision.get("decisionReason").asText()).isEqualTo("The proof is illegible");
        assertThat(decision.has("verifiedAmountCents")).isFalse();
        assertThat(names(payments.events)).containsExactly("PaymentRejected");
        assertThat(CatalogHttpTest.json(exchange(decide("reject", Map.of("reason", "The proof is illegible"), "decision-key-011", token(ADMIN)), 200)))
                .isEqualTo(decision);
        exchange(decide("approve", Map.of("verifiedAmountCents", 90_000L), "decision-key-012", token(ADMIN)), 422);
    }

    // AC-10.20: a decision after the deadline cancels the reservation; while that cancellation is not built the refusal says so, not
    // a sentence about reactivating an item.
    @Test void aDecisionAfterTheDeadlineSaysWhyItIsRefused() throws Exception {
        payments.deadline = new co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline(
                co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind.INITIAL_PAYMENT, NOW.minus(Duration.ofHours(1)));
        payments.cancellationAvailable = false;

        var refused = CatalogHttpTest.json(exchange(decide("reject", Map.of("reason", "Late"), "decision-key-020", token(ADMIN)), 422));

        assertThat(refused.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(refused.get("message").asText()).contains("cancellation for non-payment").doesNotContain("reactivated");
        assertThat(payments.cancelled).isEmpty();
    }

    @Test void aProofOfAnotherTenantOrAnUnknownOneLooksMissing() throws Exception {
        exchange(post(BASE + "/payment-proofs/" + new UUID(21, 99) + "/approve").header("Authorization", "Bearer " + token(ADMIN))
                .header("Idempotency-Key", "decision-key-013").header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("verifiedAmountCents", 90_000L))), 404);
    }

    @Test void step2OfTheSagaRecordsTheValidatedPaymentForTheForwardedUserAndRepeatsIdempotently() throws Exception {
        String service = CustomerHttpTest.token(null, "SERVICE", List.of());
        UUID approver = new UUID(21, 7);
        var body = new LinkedHashMap<String, Object>();
        body.put("approvedBy", approver.toString()); body.put("verifiedAmountCents", 90_000L);

        var first = CatalogHttpTest.json(exchange(step2(tenant, body, service), 200));
        var again = CatalogHttpTest.json(exchange(step2(tenant, body, service), 200));

        assertThat(first.get("validationStatus").asText()).isEqualTo("APPROVED");
        assertThat(first.get("decidedBy").asText()).isEqualTo(approver.toString());
        assertThat(first.get("reservationStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(again).isEqualTo(first);
        assertThat(names(payments.events)).containsExactly("PaymentValidated", "ReservationConfirmed");
    }

    @Test void step2IsForTheServiceTokenOnlyAndNamesWhatIsMissing() throws Exception {
        String service = CustomerHttpTest.token(null, "SERVICE", List.of());
        var body = new LinkedHashMap<String, Object>();
        body.put("approvedBy", new UUID(21, 7).toString()); body.put("verifiedAmountCents", 90_000L);

        exchange(step2(tenant, body, token(ADMIN)), 403);
        CatalogHttpTest.assertField(exchange(step2(tenant, Map.of("verifiedAmountCents", 90_000L), service), 400), "approvedBy");
        CatalogHttpTest.assertField(exchange(step2(tenant, Map.of("approvedBy", new UUID(21, 7).toString()), service), 400), "verifiedAmountCents");
        exchange(step2(otherTenant, body, service), 404);
        assertThat(payments.events).isEmpty();
    }
}
