package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// HU-COM-014 over HTTP (AC-14.1, AC-14.8): the list and the read of a reservation carry the payment type and method, the validated
// value, the state of the latest proof and the next action the Final Customer may take. The service decides them; the portal displays.
class ConsultationHttpTest extends ReservationHttpBase {

    private static final UUID ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT), ADMIN_ACTOR = new UUID(14, 99);

    @BeforeEach void linkTheCustomer() {
        customers.save(customers.customers.get(customer).linkedTo(ACCOUNT));
    }

    private com.fasterxml.jackson.databind.JsonNode createOwn(String key) throws Exception {
        var body = createBody(); body.remove("customerId");
        return CatalogHttpTest.json(exchange(create(body, key, FINAL_CUSTOMER), 201));
    }

    @Test void aNewReservationOffersToContinueThePaymentAndShowsNoProof() throws Exception {
        var created = createOwn("reservation-key-141");
        var row = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations"), FINAL_CUSTOMER), 200)).at("/data/0");
        assertThat(row.get("paymentType").asText()).isEqualTo(created.get("paymentType").asText());
        assertThat(row.get("paymentMethod").asText()).isEqualTo(created.get("paymentMethod").asText());
        assertThat(row.get("validatedValueCents").asLong()).isZero();
        assertThat(row.get("continueAction").asText()).isEqualTo("CONTINUE_PAYMENT");
        assertThat(row.has("proofStatus")).isFalse();
        var read = CatalogHttpTest.json(exchange(
                withToken(get(BASE + "/reservations/" + created.get("reservationId").asText()), FINAL_CUSTOMER), 200));
        assertThat(read.has("validatedValueCents")).isFalse();
        assertThat(read.get("continueAction").asText()).isEqualTo("CONTINUE_PAYMENT");
        assertThat(read.has("proofStatus")).isFalse();
    }

    @Test void aRejectedProofShowsItsStateAndOffersANewReceiptInTheListAndInTheRead() throws Exception {
        var created = createOwn("reservation-key-142");
        UUID id = UUID.fromString(created.get("reservationId").asText()), attempt = new UUID(14, 1);
        var stored = store.reservations.get(id);
        stored.registerTransfer(attempt, new Cents(100_000), new Cents(100_000), ADMIN_ACTOR, NOW.minusSeconds(7_200));
        stored.reject(attempt, "illegible", ADMIN_ACTOR, NOW.minusSeconds(3_600));

        var row = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations"), FINAL_CUSTOMER), 200)).at("/data/0");
        assertThat(row.get("proofStatus").asText()).isEqualTo("REJECTED");
        assertThat(row.get("continueAction").asText()).isEqualTo("PRESENT_NEW_RECEIPT");
        var read = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + id), FINAL_CUSTOMER), 200));
        assertThat(read.get("proofStatus").asText()).isEqualTo("REJECTED");
        assertThat(read.get("continueAction").asText()).isEqualTo("PRESENT_NEW_RECEIPT");
    }

    @Test void aCancelledReservationShowsItsCauseInTheListAndInTheReadAndAnOpenOneShowsNone() throws Exception {
        var open = createOwn("reservation-key-143");
        var cancelled = createOwn("reservation-key-144");
        UUID id = UUID.fromString(cancelled.get("reservationId").asText());
        store.reservations.get(id).cancel(CancellationCause.CUSTOMER_REQUEST, ADMIN_ACTOR, NOW, 0);

        var rows = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations?scope=all"), FINAL_CUSTOMER), 200)).get("data");
        for (var row : rows) {
            boolean isCancelled = row.get("reservationId").asText().equals(id.toString());
            assertThat(row.has("cancellationCause")).isEqualTo(isCancelled);
            if (isCancelled) assertThat(row.get("cancellationCause").asText()).isEqualTo("CUSTOMER_REQUEST");
        }
        var read = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + id), FINAL_CUSTOMER), 200));
        assertThat(read.get("cancellationCause").asText()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(CatalogHttpTest.json(exchange(
                withToken(get(BASE + "/reservations/" + open.get("reservationId").asText()), FINAL_CUSTOMER), 200)).has("cancellationCause")).isFalse();
    }

    @Test void thePaymentsOfAnOwnReservationListNewestFirstWithTheReasonOfTheRejectedOne() throws Exception {
        var created = createOwn("reservation-key-145");
        UUID id = UUID.fromString(created.get("reservationId").asText()), first = new UUID(14, 2), second = new UUID(14, 3);
        var stored = store.reservations.get(id);
        stored.registerTransfer(first, new Cents(100_000), new Cents(100_000), ADMIN_ACTOR, NOW.minusSeconds(7_200));
        stored.reject(first, "illegible", ADMIN_ACTOR, NOW.minusSeconds(3_600));
        stored.registerTransfer(second, new Cents(100_000), new Cents(100_000), ADMIN_ACTOR, NOW.minusSeconds(1_800));

        var page = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + id + "/payments"), FINAL_CUSTOMER), 200));
        assertThat(page.at("/data/0/paymentId").asText()).isEqualTo(second.toString());
        assertThat(page.at("/data/1/paymentId").asText()).isEqualTo(first.toString());
        assertThat(page.at("/data/1/validationStatus").asText()).isEqualTo("REJECTED");
        assertThat(page.at("/data/1/decisionReason").asText()).isEqualTo("illegible");
        assertThat(page.at("/data/0/decisionReason").isMissingNode()).isTrue();
    }
}
