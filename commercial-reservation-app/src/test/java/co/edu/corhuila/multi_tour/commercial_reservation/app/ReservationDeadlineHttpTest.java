package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// HU-COM-008: the create answer, the read of one and the own list carry paymentDeadline, the deadline in force (extension counted,
// never past the departure start); it is absent when the reservation has no open deadline.
class ReservationDeadlineHttpTest extends ReservationHttpBase {

    private static final UUID ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT);

    @BeforeEach void linkTheCustomer() {
        customers.save(customers.customers.get(customer).linkedTo(ACCOUNT));
    }

    private com.fasterxml.jackson.databind.JsonNode createOwn(String key) throws Exception {
        var body = createBody(); body.remove("customerId");
        return CatalogHttpTest.json(exchange(create(body, key, FINAL_CUSTOMER), 201));
    }

    private com.fasterxml.jackson.databind.JsonNode read(com.fasterxml.jackson.databind.JsonNode created) throws Exception {
        return CatalogHttpTest.json(exchange(
                withToken(get(BASE + "/reservations/" + created.get("reservationId").asText()), FINAL_CUSTOMER), 200));
    }

    @Test void aReservationWithNoOpenDeadlineShowsNoPaymentDeadline() throws Exception {
        var created = createOwn("reservation-key-081");
        assertThat(created.has("paymentDeadline")).isFalse();
        assertThat(read(created).has("paymentDeadline")).isFalse();
        assertThat(CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations"), FINAL_CUSTOMER), 200))
                .at("/data/0").has("paymentDeadline")).isFalse();
    }

    @Test void theReadAndTheOwnListCarryTheDeadlineInForceWithItsExtension() throws Exception {
        var created = createOwn("reservation-key-082");
        Instant due = NOW.plus(Duration.ofHours(6));
        payments.deadline = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, due, 2, NOW, new UUID(8, 9), null, null);
        assertThat(Instant.parse(read(created).get("paymentDeadline").asText())).isEqualTo(due.plus(Duration.ofHours(2)));
        var row = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations"), FINAL_CUSTOMER), 200)).at("/data/0");
        assertThat(Instant.parse(row.get("paymentDeadline").asText())).isEqualTo(due.plus(Duration.ofHours(2)));
    }

    @Test void theDeadlineInForceIsCappedAtTheDepartureStart() throws Exception {
        var created = createOwn("reservation-key-083");
        Instant start = NOW.plusSeconds(86_400);
        payments.deadline = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, start.minus(Duration.ofHours(1)), 5, NOW, new UUID(8, 9), null, null);
        assertThat(Instant.parse(read(created).get("paymentDeadline").asText())).isEqualTo(start);
    }

    @Test void theCreateAnswerCarriesTheDeadlineWhenOneIsAlreadyOpen() throws Exception {
        Instant due = NOW.plus(Duration.ofHours(6));
        payments.deadline = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, due);
        var created = createOwn("reservation-key-084");
        assertThat(Instant.parse(created.get("paymentDeadline").asText())).isEqualTo(due);
    }
}
