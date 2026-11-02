package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-009 over HTTP (api spec T-03, T-04, T-06; AC-09.1, AC-09.2, AC-09.5, AC-09.9, AC-09.11, AC-09.16, AC-09.18): the registration
// of a transfer with its proof and of cash, the idempotent replay, the roles before the body and and the read of one attempt.
class PaymentHttpTest extends ReservationHttpBase {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final String ACCOUNT = TestJwtKeys.ACCOUNT;

    private String reservation() throws Exception {
        return CatalogHttpTest.json(exchange(create(createBody(), "reservation-key-" + UUID.randomUUID(), ADMIN), 201)).get("reservationId").asText();
    }

    private MockHttpServletRequestBuilder transfer(String reservation, long amount, byte[] proof, String key, String role) throws Exception {
        var request = withToken(multipart(BASE + "/reservations/" + reservation + "/payments")
                .file(new MockMultipartFile("payment", "", "application/json",
                        TestJwtKeys.JSON.writeValueAsBytes(Map.of("paymentMethod", "TRANSFER", "amountCents", amount))))
                .file(new MockMultipartFile("proof", "proof.png", "image/png", proof)), role);
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private MockHttpServletRequestBuilder cash(String reservation, Map<String, Object> body, String key, String role) throws Exception {
        var request = withToken(post(BASE + "/reservations/" + reservation + "/payments"), role).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    @Test void aCollaboratorRegistersATransferWithItsProofAndItIsStoredInValidation() throws Exception {
        String id = reservation();

        var result = exchange(transfer(id, 90_000, PNG, "payment-key-0001", COLLABORATOR), 201);

        JsonNode body = CatalogHttpTest.json(result);
        assertThat(body.get("paymentMethod").asText()).isEqualTo("TRANSFER");
        assertThat(body.get("validationStatus").asText()).isEqualTo("IN_VALIDATION");
        assertThat(body.get("registeredBy").asText()).isEqualTo(ACCOUNT);
        assertThat(body.get("reservationStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(body.get("pendingBalanceCents").asLong()).isEqualTo(300_000L);
        assertThat(body.get("seatHeld").asBoolean()).isTrue();
        assertThat(body.has("proof")).isFalse();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/reservations/" + id + "/payments/" + body.get("paymentId").asText());
        assertThat(payments.proofs).containsKey(UUID.fromString(body.get("paymentId").asText()));
        assertThat(payments.events.stream().map(PaymentEvent::getClass).map(Class::getSimpleName).toList())
                .containsExactly("PaymentEvidenceRegistered", "CapacityAllocated");
    }

    @Test void theSameKeyAnswersTheFirstPaymentWithOkAndStoresNothingMore() throws Exception {
        String id = reservation();
        var first = CatalogHttpTest.json(exchange(transfer(id, 90_000, PNG, "payment-key-0002", ADMIN), 201));
        int events = payments.events.size();

        var again = CatalogHttpTest.json(exchange(transfer(id, 90_000, PNG, "payment-key-0002", ADMIN), 200));

        assertThat(again).isEqualTo(first);
        assertThat(payments.events).hasSize(events);
        assertThat(store.reservations.values().iterator().next().attempts()).hasSize(1);
    }

    @Test void cashIsMoneyReceivedAtOnceAndConfirmsTheReservationWhenTheDepositIsMet() throws Exception {
        String id = reservation();

        var body = CatalogHttpTest.json(exchange(cash(id, Map.of("paymentMethod", "CASH", "amountCents", 90_000L), "payment-key-0003", ADMIN), 201));

        assertThat(body.has("validationStatus")).isFalse();
        assertThat(body.get("reservationStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(body.get("pendingBalanceCents").asLong()).isEqualTo(210_000L);
        assertThat(payments.income).hasSize(1);
    }

    @Test void cashWithNoOpenSessionIsRefusedWithNothingStored() throws Exception {
        String id = reservation();
        payments.sessionOpen = false;

        var error = CatalogHttpTest.json(exchange(cash(id, Map.of("paymentMethod", "CASH", "amountCents", 90_000L), "payment-key-0004", ADMIN), 422));

        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.get("message").asText()).contains("cash session");
        assertThat(store.reservations.values().iterator().next().attempts()).isEmpty();
    }

    @Test void theRolesThatCannotPayAreRefusedBeforeTheBodyAndAFinalCustomerCannotRegisterCash() throws Exception {
        String id = reservation();
        for (String role : List.of(MANAGER, ACCOUNTANT, ANALYST)) {
            exchange(cash(id, Map.of("nothing", 1), "payment-key-0005", role), 403);
        }
        exchange(cash(id, Map.of("paymentMethod", "CASH", "amountCents", 90_000L), "payment-key-0006", FINAL_CUSTOMER), 403);
        assertThat(store.reservations.values().iterator().next().attempts()).isEmpty();
    }

    @Test void everyMissingDatumIsNamedAndTheKeyIsRequired() throws Exception {
        String id = reservation();

        CatalogHttpTest.assertField(exchange(cash(id, Map.of("paymentMethod", "CASH"), "payment-key-0007", ADMIN), 400), "amountCents");
        CatalogHttpTest.assertField(exchange(cash(id, Map.of("paymentMethod", "CASH", "amountCents", 0), "payment-key-0008", ADMIN), 400), "amountCents");
        CatalogHttpTest.assertField(exchange(cash(id, Map.of("paymentMethod", "TRANSFER", "amountCents", 90_000L), "payment-key-0009", ADMIN), 400), "proof");
        CatalogHttpTest.assertField(exchange(cash(id, Map.of("paymentMethod", "CASH", "amountCents", 90_000L), null, ADMIN), 400), "Idempotency-Key");
        CatalogHttpTest.assertField(exchange(transfer(id, 90_000, "not an image".getBytes(), "payment-key-0010", ADMIN), 400), "proof");
        CatalogHttpTest.assertField(exchange(cash(id, Map.of("paymentMethod", "CASH", "amountCents", 90_000L, "reservationStatus", "CONFIRMED"),
                "payment-key-0011", ADMIN), 400), "reservationStatus");
        assertThat(store.reservations.values().iterator().next().attempts()).isEmpty();
    }

    @Test void aDepartureWithNoAvailabilityRefusesTheFirstPaymentAndStoresNothing() throws Exception {
        String id = reservation();
        payments.availability = false;

        exchange(transfer(id, 90_000, PNG, "payment-key-0012", ADMIN), 422);

        assertThat(store.saves).isEqualTo(1); // only the creation was saved; a real database would also roll the attempt back
        assertThat(payments.proofs).isEmpty();
        assertThat(payments.events).isEmpty();
    }

    @Test void aReservationOfAnotherTenantLooksMissing() throws Exception {
        exchange(transfer(new UUID(1, 90).toString(), 90_000, PNG, "payment-key-0013", ADMIN), 404);
        exchange(cash(new UUID(1, 90).toString(), Map.of("paymentMethod", "CASH", "amountCents", 1L), "payment-key-0014", ADMIN), 404);
    }

    @Test void oneAttemptIsReadWithoutTheFileAndAnUnknownOneLooksMissing() throws Exception {
        String id = reservation();
        var first = CatalogHttpTest.json(exchange(transfer(id, 90_000, PNG, "payment-key-0015", ADMIN), 201));

        var one = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + id + "/payments/" + first.get("paymentId").asText()), ANALYST), 200));

        assertThat(one.get("paymentId")).isEqualTo(first.get("paymentId"));
        assertThat(one.get("validationStatus").asText()).isEqualTo("IN_VALIDATION");
        exchange(withToken(get(BASE + "/reservations/" + id + "/payments/" + new UUID(1, 91)), ADMIN), 404);
    }
}
