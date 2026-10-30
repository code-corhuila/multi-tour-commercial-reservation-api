package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// POST /reservations/{id}/additional-discount (contract registerAdditionalDiscount, HU-COM-004): AC-04.4, AC-04.7 to AC-04.9,
// AC-04.11 and AC-04.14. Tenant and author are the token's; a repeat of the key answers the original; nothing else is written.
class AdditionalDiscountRegistrationHttpTest extends AdditionalDiscountHttpBase {
    private final UUID loyalty = new UUID(4, 1), disabled = new UUID(4, 2);

    @BeforeEach void enabledAndDisabledReasons() {
        reasons.save(AuthorizableReason.create(tenant, loyalty, "Customer loyalty"));
        reasons.save(AuthorizableReason.create(tenant, disabled, "Old agreement").disabled());
    }

    Map<String, Object> body(Object basisPoints, Object reasonId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (basisPoints != null) body.put("basisPoints", basisPoints);
        if (reasonId != null) body.put("reasonId", reasonId.toString());
        return body;
    }

    MockHttpServletRequestBuilder register(UUID reservation, Map<String, Object> body, String key, String role) throws Exception {
        var request = withToken(post(BASE + "/reservations/" + reservation + ADDITIONAL), role)
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    @Test void theRegistrationAnswers201WithTheUpdatedBreakdownAndTheAdditionalLineLast() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        var result = exchange(register(reservation, body(1_000, loyalty), "additional-key-1", ADMIN), 201);
        var breakdown = CatalogHttpTest.json(result);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(BASE + "/reservations/" + reservation + ADDITIONAL);
        assertThat(breakdown.get("baseCents").asLong()).isEqualTo(300_000L);
        assertThat(breakdown.get("finalValueCents").asLong()).isEqualTo(243_000L);
        assertThat(breakdown.get("lines")).hasSize(2);
        assertThat(breakdown.at("/lines/0/kind").asText()).isEqualTo("RULE");
        assertThat(breakdown.at("/lines/0/subtotalCents").asLong()).isEqualTo(270_000L);
        assertThat(breakdown.at("/lines/1/kind").asText()).isEqualTo("ADDITIONAL");
        assertThat(breakdown.at("/lines/1/name").asText()).isEqualTo("Customer loyalty");
        assertThat(breakdown.at("/lines/1/amountCents").asLong()).isEqualTo(27_000L);
        assertThat(breakdown.at("/lines/1/subtotalCents").asLong()).isEqualTo(243_000L);
    }

    @Test void theAuthorIsTheTokenAndTheTenantIsTheTokenAndTheEventCarriesTheReasonAndTheOrder() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        exchange(register(reservation, body(1_000, loyalty), "additional-key-2", ADMIN), 201);
        var stored = extras.discounts.get(reservation);
        assertThat(stored.tenantId()).isEqualTo(tenant);
        assertThat(stored.authorizerId().toString()).isEqualTo(TestJwtKeys.ACCOUNT);
        assertThat(stored.reason()).isEqualTo("Customer loyalty");
        assertThat(extras.events).hasSize(1);
        var event = extras.events.get(0);
        assertThat(event.reservationId()).isEqualTo(reservation);
        assertThat(event.previousSubtotal()).isEqualTo(new Cents(270_000));
        assertThat(event.newSubtotal()).isEqualTo(new Cents(243_000));
        assertThat(event.reason()).isEqualTo("Customer loyalty");
        assertThat(event.authorizerId().toString()).isEqualTo(TestJwtKeys.ACCOUNT);
        assertThat(event.order()).isEqualTo(2);
    }

    @Test void repeatingTheKeyAnswers200WithTheSameBreakdownAndWritesNothingMore() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        var first = CatalogHttpTest.json(exchange(register(reservation, body(1_000, loyalty), "additional-key-3", ADMIN), 201));
        var second = CatalogHttpTest.json(exchange(register(reservation, body(1_000, loyalty), "additional-key-3", ADMIN), 200));
        assertThat(second).isEqualTo(first);
        assertThat(extras.discounts).hasSize(1);
        assertThat(extras.events).hasSize(1);
    }

    @Test void theSameKeyForAnotherReservationIsABusinessRuleViolationNotAReplay() throws Exception {
        exchange(register(reservationWithTenPercentOff(), body(1_000, loyalty), "additional-key-4", ADMIN), 201);
        UUID other = reservationWithoutDiscounts();
        var error = CatalogHttpTest.json(exchange(register(other, body(1_000, loyalty), "additional-key-4", ADMIN), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(extras.discounts).doesNotContainKey(other);
    }

    @ParameterizedTest @ValueSource(strings = {"", "short"})
    void aMissingOrShortKeyIsRefusedNamingTheHeaderAndWritesNothing(String key) throws Exception {
        CatalogHttpTest.assertField(exchange(register(reservationWithTenPercentOff(), body(1_000, loyalty),
                key.isEmpty() ? null : key, ADMIN), 400), "Idempotency-Key");
        assertThat(extras.discounts).isEmpty();
    }

    @ParameterizedTest @ValueSource(ints = {0, 99, -1, 10_001})
    void aPercentageOutsideTheContractRangeIsRefusedNamingTheField(int basisPoints) throws Exception {
        CatalogHttpTest.assertField(exchange(register(reservationWithTenPercentOff(), body(basisPoints, loyalty),
                "additional-key-5", ADMIN), 400), "basisPoints");
        assertThat(extras.discounts).isEmpty();
    }

    @Test void aMissingPercentageOrReasonIsRefusedNamingTheField() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        CatalogHttpTest.assertField(exchange(register(reservation, body(null, loyalty), "additional-key-6", ADMIN), 400),
                "basisPoints");
        CatalogHttpTest.assertField(exchange(register(reservation, body(1_000, null), "additional-key-7", ADMIN), 400),
                "reasonId");
        assertThat(extras.discounts).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"tenantId", "authorizerId", "appliedBy", "reason"})
    void aTenantAnAuthorOrAFreeTextReasonInTheBodyIsRefused(String field) throws Exception {
        var body = body(1_000, loyalty); body.put(field, "x");
        CatalogHttpTest.assertField(exchange(register(reservationWithTenPercentOff(), body, "additional-key-8", ADMIN), 400), field);
        assertThat(extras.discounts).isEmpty();
    }

    @Test void aReasonThatIsNotAnEnabledOneOfTheTenantIsABusinessRuleViolation() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        for (UUID reason : new UUID[] {disabled, new UUID(4, 9)}) {
            var error = CatalogHttpTest.json(exchange(register(reservation, body(1_000, reason), "additional-key-9" + reason, ADMIN), 422));
            assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        }
        assertThat(extras.discounts).isEmpty();
        assertThat(extras.events).isEmpty();
    }

    @Test void aReservationThatIsNotPendingOrConfirmedIsABusinessRuleViolation() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        extras.states.put(reservation, ReservationSnapshot.State.CANCELLED);
        var error = CatalogHttpTest.json(exchange(register(reservation, body(1_000, loyalty), "additional-key-10", ADMIN), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.get("traceId").asText()).isEqualTo(TRACE);
        assertThat(extras.discounts).isEmpty();
    }

    @Test void aSecondDiscountWithAnotherKeyIsABusinessRuleViolationAndTheFirstStays() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        exchange(register(reservation, body(1_000, loyalty), "additional-key-11", ADMIN), 201);
        var error = CatalogHttpTest.json(exchange(register(reservation, body(500, loyalty), "additional-key-12", ADMIN), 422));
        assertThat(error.get("message").asText()).contains("already has an additional discount");
        assertThat(extras.discounts.get(reservation).rate().value()).isEqualTo(1_000);
        assertThat(extras.events).hasSize(1);
    }

    @Test void whatWasPaidAboveTheNewValueOpensTheTreatmentOnce() throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        extras.paid.put(reservation, new Cents(280_000));
        exchange(register(reservation, body(1_000, loyalty), "additional-key-13", ADMIN), 201);
        exchange(register(reservation, body(1_000, loyalty), "additional-key-13", ADMIN), 200);
        assertThat(extras.excesses).containsExactly(new Cents(37_000));
    }

    @ParameterizedTest @ValueSource(strings = {COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER})
    void everyRoleButTheTenantAdministratorIsRefusedAsAPermissionMatterBeforeAnyValidation(String role) throws Exception {
        UUID reservation = reservationWithTenPercentOff();
        assertThat(CatalogHttpTest.json(exchange(register(reservation, body(1_000, loyalty), "additional-key-14", role), 403))
                .get("error").asText()).isEqualTo("FORBIDDEN");
        assertThat(CatalogHttpTest.json(exchange(register(reservation, body(1, null), null, role), 403)).get("error").asText())
                .isEqualTo("FORBIDDEN");
        assertThat(extras.discounts).isEmpty();
    }

    @Test void noTokenIsUnauthorized() throws Exception {
        mvc.perform(post(BASE + "/reservations/" + reservationWithTenPercentOff() + ADDITIONAL)
                .header("X-Correlation-Id", TRACE).header("Idempotency-Key", "additional-key-15")
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body(1_000, loyalty))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test void aReservationOfAnotherTenantOrAMissingOneAnswersTheSameNotFoundAndWritesNothing() throws Exception {
        UUID foreign = reservationWithTenPercentOff(otherTenant);
        var foreignError = CatalogHttpTest.json(exchange(register(foreign, body(1_000, loyalty), "additional-key-16", ADMIN), 404));
        var missing = CatalogHttpTest.json(exchange(register(UUID.randomUUID(), body(1_000, loyalty), "additional-key-17", ADMIN), 404));
        assertThat(foreignError.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(foreignError.get("message")).isEqualTo(missing.get("message"));
        assertThat(extras.discounts).isEmpty();
        assertThat(extras.events).isEmpty();
    }
}
