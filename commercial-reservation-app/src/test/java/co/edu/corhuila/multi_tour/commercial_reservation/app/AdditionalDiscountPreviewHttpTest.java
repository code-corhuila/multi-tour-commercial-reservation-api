package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BasisPoints;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// GET /reservations/{id}/additional-discount/preview (contract previewAdditionalDiscount, HU-COM-004): AC-04.13 and the
// roles, tenant and error answers of the row. A preview is a read: it stores nothing and sends no event.
class AdditionalDiscountPreviewHttpTest extends AdditionalDiscountHttpBase {

    @Test void thePreviewShowsTheFixedLinesThenTheAdditionalOneLastWithTheValuesBeforeAndAfter() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        var preview = CatalogHttpTest.json(exchange(preview(reservation, "1000", ADMIN), 200));
        assertThat(preview.get("previousFinalValueCents").asLong()).isEqualTo(270_000L);
        assertThat(preview.get("newFinalValueCents").asLong()).isEqualTo(243_000L);
        assertThat(preview.get("excessCents").asLong()).isZero();
        var breakdown = preview.get("breakdown");
        assertThat(breakdown.get("baseCents").asLong()).isEqualTo(300_000L);
        assertThat(breakdown.get("finalValueCents").asLong()).isEqualTo(243_000L);
        var lines = breakdown.get("lines");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).get("kind").asText()).isEqualTo("RULE");
        assertThat(lines.get(0).get("order").asInt()).isEqualTo(1);
        assertThat(lines.get(0).get("name").asText()).isEqualTo("Early booking");
        assertThat(lines.get(0).get("discountRuleId").asText()).isEqualTo(TEN_PERCENT_RULE.toString());
        assertThat(lines.get(0).get("calculationBase").asText()).isEqualTo("ORIGINAL_VALUE");
        assertThat(lines.get(0).get("subtotalCents").asLong()).isEqualTo(270_000L);
        assertThat(lines.get(1).get("kind").asText()).isEqualTo("ADDITIONAL");
        assertThat(lines.get(1).get("order").asInt()).isEqualTo(2);
        assertThat(lines.get(1).get("calculationBase").asText()).isEqualTo("PREVIOUS_SUBTOTAL");
        assertThat(lines.get(1).get("baseCents").asLong()).isEqualTo(270_000L);
        assertThat(lines.get(1).get("amountCents").asLong()).isEqualTo(27_000L);
        assertThat(lines.get(1).get("subtotalCents").asLong()).isEqualTo(243_000L);
        assertThat(lines.get(1).has("discountRuleId")).isFalse();
    }

    @Test void aReservationWithoutFixedDiscountsHasOnlyTheAdditionalLine() throws Exception {
        var preview = CatalogHttpTest.json(exchange(preview(reservationWithoutDiscounts(), "100", ADMIN), 200));
        assertThat(preview.at("/breakdown/lines")).hasSize(1);
        assertThat(preview.at("/breakdown/lines/0/order").asInt()).isEqualTo(1);
        assertThat(preview.at("/breakdown/lines/0/amountCents").asLong()).isEqualTo(3_000L);
        assertThat(preview.get("newFinalValueCents").asLong()).isEqualTo(297_000L);
    }

    @Test void whatWasPaidAboveTheNewValueIsTheExcessAndNothingIsOpened() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        extras.paid.put(reservation, new Cents(280_000));
        var preview = CatalogHttpTest.json(exchange(preview(reservation, "1000", ADMIN), 200));
        assertThat(preview.get("excessCents").asLong()).isEqualTo(37_000L);
        assertThat(extras.excesses).isEmpty();
    }

    @Test void aPreviewStoresNothingAndSendsNoEvent() throws Exception {
        exchange(preview(reservationWithTenPercentOff(), "1000", ADMIN), 200);
        assertThat(extras.discounts).isEmpty();
        assertThat(extras.events).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER})
    void everyRoleButTheTenantAdministratorIsRefusedAsAPermissionMatterBeforeAnyValidation(String role) throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        assertThat(CatalogHttpTest.json(exchange(preview(reservation, "1000", role), 403)).get("error").asText())
                .isEqualTo("FORBIDDEN");
        // The permission precedes the validation: a refused caller learns nothing about the query.
        assertThat(CatalogHttpTest.json(exchange(preview(reservation, "abc", role), 403)).get("error").asText())
                .isEqualTo("FORBIDDEN");
    }

    @Test void noTokenIsUnauthorized() throws Exception {
        mvc.perform(get(BASE + "/reservations/" + reservationWithTenPercentOff() + ADDITIONAL + "/preview?basisPoints=1000")
                .header("X-Correlation-Id", TRACE)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isUnauthorized());
    }

    @Test void aReservationOfAnotherTenantOrAMissingOneAnswersTheSameNotFound() throws Exception {
        var foreign = CatalogHttpTest.json(exchange(preview(reservationWithTenPercentOff(otherTenant), "1000", ADMIN), 404));
        var missing = CatalogHttpTest.json(exchange(preview(UUID.randomUUID(), "1000", ADMIN), 404));
        assertThat(foreign.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(foreign.get("message")).isEqualTo(missing.get("message"));
    }

    @ParameterizedTest @ValueSource(strings = {"99", "0", "-5", "10001", "abc", "10.5"})
    void aPercentageOutsideTheContractRangeIsRefusedNamingTheField(String basisPoints) throws Exception {
        CatalogHttpTest.assertField(exchange(preview(reservationWithTenPercentOff(), basisPoints, ADMIN), 400), "basisPoints");
    }

    @Test void aMissingPercentageOrAnUnknownParameterIsRefused() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        CatalogHttpTest.assertField(exchange(preview(reservation, null, ADMIN), 400), "basisPoints");
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/reservations/" + reservation + ADDITIONAL
                + "/preview?basisPoints=500&tenantId=" + tenant), ADMIN), 400), "tenantId");
    }

    @Test void aReservationThatIsNotPendingOrConfirmedIsABusinessRuleViolation() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        extras.states.put(reservation, ReservationSnapshot.State.CANCELLED);
        var error = CatalogHttpTest.json(exchange(preview(reservation, "1000", ADMIN), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.get("message").asText()).contains("pending or confirmed");
        assertThat(error.get("traceId").asText()).isEqualTo(TRACE);
    }

    @Test void aReservationThatAlreadyHasAnAdditionalDiscountIsABusinessRuleViolation() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        var rate = new BasisPoints(500);
        extras.discounts.put(reservation, new AdditionalDiscount(tenant, reservation, rate,
                DiscountLine.percentage(new Cents(270_000), rate), "Loyal customer", new UUID(3, 3), 2, NOW));
        var error = CatalogHttpTest.json(exchange(preview(reservation, "1000", ADMIN), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.get("message").asText()).contains("already has an additional discount");
    }

    @Test void aSubtotalSoSmallThatThePercentageTakesZeroCentsAnswersTheSameBusinessRuleViolationAsTheRegistration() throws Exception {
        var error = CatalogHttpTest.json(exchange(preview(reservationWithSubtotal(49), "100", ADMIN), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.get("message").asText()).contains("at least one cent");
        exchange(preview(reservationWithSubtotal(50), "100", ADMIN), 200);
    }
}
