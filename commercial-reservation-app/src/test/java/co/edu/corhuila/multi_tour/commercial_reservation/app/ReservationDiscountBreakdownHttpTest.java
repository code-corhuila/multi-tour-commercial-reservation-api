package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BasisPoints;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// GET /reservations/{id}/discount-breakdown (contract getReservationDiscountBreakdown, HU-COM-004, AC-04.3, AC-04.4, AC-04.12):
// the same roles as the reservation read, a Final Customer only their own, and the same 404 for a missing or foreign one.
class ReservationDiscountBreakdownHttpTest extends AdditionalDiscountHttpBase {
    private static final UUID AUTHOR = new UUID(3, 3);

    private MockHttpServletRequestBuilder breakdown(UUID reservation, String role) throws Exception {
        return withToken(get(BASE + "/reservations/" + reservation + "/discount-breakdown"), role);
    }

    private void plantAdditional(UUID reservation, String reason) {
        var rate = new BasisPoints(1_000);
        extras.discounts.put(reservation, new AdditionalDiscount(tenant, reservation, rate,
                DiscountLine.percentage(new Cents(270_000), rate), reason, AUTHOR, 2, NOW));
    }

    @Test void withoutAnAdditionalDiscountTheFixedLinesAreShownAndNoAdditionalFieldIsPresent() throws Exception {
        var body = CatalogHttpTest.json(exchange(breakdown(reservationWithTenPercentOff(), ADMIN), 200));
        assertThat(body.at("/breakdown/baseCents").asLong()).isEqualTo(300_000L);
        assertThat(body.at("/breakdown/finalValueCents").asLong()).isEqualTo(270_000L);
        assertThat(body.at("/breakdown/lines")).hasSize(1);
        assertThat(body.at("/breakdown/lines/0/kind").asText()).isEqualTo("RULE");
        assertThat(body.at("/breakdown/lines/0/name").asText()).isEqualTo("Early booking");
        assertThat(body.has("previousFinalValueCents")).isFalse();
        assertThat(body.has("newFinalValueCents")).isFalse();
        assertThat(body.has("excessCents")).isFalse();
        assertThat(body.has("additionalDiscount")).isFalse();
    }

    @Test void aReservationWithoutAnyDiscountHasAnEmptyLinesList() throws Exception {
        var body = CatalogHttpTest.json(exchange(breakdown(reservationWithoutDiscounts(), ADMIN), 200));
        assertThat(body.at("/breakdown/lines")).isEmpty();
        assertThat(body.at("/breakdown/finalValueCents").asLong()).isEqualTo(300_000L);
    }

    @Test void withAnAdditionalDiscountItIsTheLastLineWithTheValuesTheAuthorAndTheDate() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        plantAdditional(reservation, "Loyal customer");
        var body = CatalogHttpTest.json(exchange(breakdown(reservation, ADMIN), 200));
        var lines = body.at("/breakdown/lines");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).get("kind").asText()).isEqualTo("RULE");
        assertThat(lines.get(1).get("kind").asText()).isEqualTo("ADDITIONAL");
        assertThat(lines.get(1).get("name").asText()).isEqualTo("Loyal customer");
        assertThat(lines.get(1).get("amountCents").asLong()).isEqualTo(27_000L);
        assertThat(lines.get(1).get("subtotalCents").asLong()).isEqualTo(243_000L);
        assertThat(body.at("/breakdown/finalValueCents").asLong()).isEqualTo(243_000L);
        assertThat(body.get("previousFinalValueCents").asLong()).isEqualTo(270_000L);
        assertThat(body.get("newFinalValueCents").asLong()).isEqualTo(243_000L);
        assertThat(body.get("excessCents").asLong()).isZero();
        assertThat(body.at("/additionalDiscount/authorId").asText()).isEqualTo(AUTHOR.toString());
        assertThat(body.at("/additionalDiscount/registeredAt").asText()).isEqualTo(NOW.toString());
    }

    @Test void whatWasValidatedAboveTheNewValueIsTheExcess() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        plantAdditional(reservation, "Loyal customer");
        store.reservations.get(reservation).restoredAs(ReservationStatus.CONFIRMED, PaymentStatus.PARTIAL, new Cents(0), true,
                List.of());
        var body = CatalogHttpTest.json(exchange(breakdown(reservation, ADMIN), 200));
        assertThat(body.get("excessCents").asLong()).isEqualTo(27_000L);
    }

    @Test void theReservationReadStaysFixedAtCreation() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        plantAdditional(reservation, "Loyal customer");
        var read = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + reservation), ADMIN), 200));
        assertThat(read.get("finalValueCents").asLong()).isEqualTo(270_000L);
    }

    @ParameterizedTest @ValueSource(strings = {ADMIN, COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST})
    void everyOperatorRoleThatReadsReservationsReadsTheBreakdown(String role) throws Exception {
        exchange(breakdown(reservationWithTenPercentOff(), role), 200);
    }

    @Test void aFinalCustomerReadsTheBreakdownOfTheirOwnReservationOnly() throws Exception {
        customers.save(customers.customers.get(customer).linkedTo(UUID.fromString(TestJwtKeys.ACCOUNT)));
        UUID own = reservationWithTenPercentOff();
        exchange(breakdown(own, FINAL_CUSTOMER), 200);
        UUID foreign = reservationWithTenPercentOff(otherTenant);
        var other = CatalogHttpTest.json(exchange(breakdown(foreign, FINAL_CUSTOMER), 404));
        var missing = CatalogHttpTest.json(exchange(breakdown(UUID.randomUUID(), FINAL_CUSTOMER), 404));
        assertThat(other.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(other.get("message")).isEqualTo(missing.get("message"));
    }

    @Test void aFinalCustomerWithoutALinkedCustomerSeesTheSameNotFound() throws Exception {
        exchange(breakdown(reservationWithTenPercentOff(), FINAL_CUSTOMER), 404);
    }

    @Test void aReservationOfAnotherTenantOrAMissingOneAnswersTheSameNotFound() throws Exception {
        var foreign = CatalogHttpTest.json(exchange(breakdown(reservationWithTenPercentOff(otherTenant), ADMIN), 404));
        var missing = CatalogHttpTest.json(exchange(breakdown(UUID.randomUUID(), ADMIN), 404));
        assertThat(foreign.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(foreign.get("message")).isEqualTo(missing.get("message"));
        assertThat(foreign.get("traceId").asText()).isEqualTo(TRACE);
    }

    @Test void noTokenIsUnauthorized() throws Exception {
        mvc.perform(get(BASE + "/reservations/" + reservationWithTenPercentOff() + "/discount-breakdown")
                .header("X-Correlation-Id", TRACE)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isUnauthorized());
    }

    @Test void aQueryCannotChooseTheTenant() throws Exception {
        UUID foreign = reservationWithTenPercentOff(otherTenant);
        exchange(withToken(get(BASE + "/reservations/" + foreign + "/discount-breakdown?tenantId=" + otherTenant), ADMIN), 404);
    }

    @Test void theBreakdownReadStoresNothingAndSendsNoEvent() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        exchange(breakdown(reservation, ADMIN), 200);
        assertThat(extras.discounts).isEmpty();
        assertThat(extras.events).isEmpty();
    }
}
