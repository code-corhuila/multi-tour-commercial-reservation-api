package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-009 over HTTP (api spec T-02, T-05; AC-09.3, AC-09.15, AC-09.16, AC-09.17): the history of the payments, the quote computed by
// the service and the payment parameters with their bounds, their reason and the Administrator as the only author.
class PaymentQuoteHttpTest extends ReservationHttpBase {

    private String reservation() throws Exception {
        return CatalogHttpTest.json(exchange(create(createBody(), "reservation-key-" + UUID.randomUUID(), ADMIN), 201)).get("reservationId").asText();
    }

    private MockHttpServletRequestBuilder cash(String reservation, long amount, String key) throws Exception {
        return withToken(post(BASE + "/reservations/" + reservation + "/payments"), ADMIN).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("paymentMethod", "CASH", "amountCents", amount)));
    }

    private Map<String, Object> row(String type) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("paymentType", type); row.put("initialWaitHours", 24); row.put("additionalWaitHours", 48);
        row.put("transferEnabled", true); row.put("cashEnabled", true);
        if (type.equals("DEPOSIT")) { row.put("minimumDepositBp", 3000); row.put("balanceDeadlineAdvanceHours", 72); }
        return row;
    }

    private MockHttpServletRequestBuilder change(Map<String, Object> body, String role) throws Exception {
        return change(body, role, "param-key-" + UUID.randomUUID());
    }

    private MockHttpServletRequestBuilder change(Map<String, Object> body, String role, String key) throws Exception {
        return withToken(put(BASE + "/payment-parameters"), role).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
    }

    @Test void theHistoryIsPagedAndRefusesUnknownParameters() throws Exception {
        String id = reservation();
        exchange(cash(id, 90_000, "payment-key-1001"), 201);
        exchange(cash(id, 10_000, "payment-key-1002"), 201);

        var page = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + id + "/payments").param("limit", "1"), ACCOUNTANT), 200));

        assertThat(page.at("/meta/total").asInt()).isEqualTo(2);
        assertThat(page.at("/meta/totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("data")).hasSize(1);
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/reservations/" + id + "/payments").param("limit", "0"), ADMIN), 400), "limit");
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/reservations/" + id + "/payments").param("sort", "x"), ADMIN), 400), "sort");
    }

    @Test void theQuoteIsComputedByTheServiceFromTheEnabledTypesAndTheMinimum() throws Exception {
        String id = reservation();

        JsonNode quote = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + id + "/payment-quote"), COLLABORATOR), 200));

        assertThat(quote.get("paymentType").asText()).isEqualTo("DEPOSIT");
        assertThat(quote.get("amountToPayCents").asLong()).isEqualTo(90_000L);
        assertThat(quote.get("minimumDepositCents").asLong()).isEqualTo(90_000L);
        assertThat(quote.get("pendingBalanceCents").asLong()).isEqualTo(300_000L);
        assertThat(quote.get("enabledTypes")).hasSize(2);
        assertThat(quote.get("enabledMethods")).hasSize(2);
        JsonNode full = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + id + "/payment-quote").param("type", "FULL_PAYMENT"), ADMIN), 200));
        assertThat(full.get("amountToPayCents").asLong()).isEqualTo(300_000L);
        assertThat(full.has("minimumDepositCents")).isFalse();
        assertThat(store.saves).isEqualTo(1);
    }

    @Test void theQuoteRefusesWhatItCannotAnswer() throws Exception {
        String id = reservation();
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/reservations/" + id + "/payment-quote").param("type", "LOAN"), ADMIN), 400), "type");
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/reservations/" + id + "/payment-quote").param("amount", "1"), ADMIN), 400), "amount");
        for (String role : List.of(MANAGER, ACCOUNTANT, ANALYST)) exchange(withToken(get(BASE + "/reservations/" + id + "/payment-quote"), role), 403);
        exchange(withToken(get(BASE + "/reservations/" + new UUID(1, 92) + "/payment-quote"), ADMIN), 404);
        exchange(withToken(get(BASE + "/reservations/" + id + "/payment-quote"), FINAL_CUSTOMER), 404);
    }

    @Test void theAdministratorChangesTheParametersWithAReasonAndTheOperatingRolesReadThem() throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("reason", "New season"); body.put("parameters", List.of(row("DEPOSIT"), row("FULL_PAYMENT")));

        var changed = CatalogHttpTest.json(exchange(change(body, ADMIN), 200));
        var read = CatalogHttpTest.json(exchange(withToken(get(BASE + "/payment-parameters"), MANAGER), 200));

        assertThat(changed.get("data")).hasSize(2);
        assertThat(read.at("/meta/total").asInt()).isEqualTo(2);
        assertThat(read.at("/data/0/changedBy").asText()).isEqualTo(TestJwtKeys.ACCOUNT);
        assertThat(read.at("/data/0/changeReason").asText()).isEqualTo("New season");
        assertThat(read.at("/data/0/minimumDepositBp").asInt()).isEqualTo(3000);
        assertThat(read.at("/data/1/minimumDepositBp").isMissingNode()).isTrue();
    }

    @Test void onlyTheAdministratorChanges() throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("reason", "New season"); body.put("parameters", List.of(row("FULL_PAYMENT")));
        for (String role : List.of(COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER)) exchange(change(body, role), 403);
        assertThat(payments.parameters).isEmpty();
    }

    // PDR 1.9 and AC-RS.4: the Final Customer knows the enabled types and methods before the reservation exists, with no author,
    // reason, time, hours or minimum deposit. The decision is the reduced projection, taken by default.
    @Test void theFinalCustomerReadsOnlyTheTypeAndTheTwoEnabledMethods() throws Exception {
        var body = new LinkedHashMap<String, Object>();
        var cashOff = row("FULL_PAYMENT"); cashOff.put("cashEnabled", false);
        body.put("reason", "New season"); body.put("parameters", List.of(row("DEPOSIT"), cashOff));
        exchange(change(body, ADMIN), 200);

        var customer = CatalogHttpTest.json(exchange(withToken(get(BASE + "/payment-parameters"), FINAL_CUSTOMER), 200));
        var full = CatalogHttpTest.json(exchange(withToken(get(BASE + "/payment-parameters"), ADMIN), 200));

        assertThat(customer.at("/meta/total").asInt()).isEqualTo(2);
        for (JsonNode item : customer.get("data")) {
            List<String> names = new java.util.ArrayList<>();
            item.fieldNames().forEachRemaining(names::add);
            assertThat(names).containsExactlyInAnyOrder("paymentType", "transferEnabled", "cashEnabled");
        }
        assertThat(customer.at("/data/1/paymentType").asText()).isEqualTo("FULL_PAYMENT");
        assertThat(customer.at("/data/1/cashEnabled").asBoolean()).isFalse();
        for (String hidden : List.of("changedBy", "changeReason", "changedAt", "initialWaitHours", "additionalWaitHours")) {
            assertThat(customer.toString()).doesNotContain(hidden);
            assertThat(full.at("/data/0/" + hidden).isMissingNode()).isFalse();
        }
        assertThat(customer.toString()).doesNotContain("minimumDepositBp").doesNotContain("balanceDeadlineAdvanceHours");
        assertThat(full.at("/data/0/minimumDepositBp").asInt()).isEqualTo(3000);
    }

    @Test void theFinalCustomerCannotChangeAndAnonymousCallersAreRefused() throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("reason", "New season"); body.put("parameters", List.of(row("FULL_PAYMENT")));
        exchange(change(body, FINAL_CUSTOMER), 403);
        exchange(get(BASE + "/payment-parameters").header("X-Correlation-Id", TRACE), 401);
        assertThat(payments.parameters).isEmpty();
    }

    @Test void everyInvalidFieldIsNamedAndNothingIsWritten() throws Exception {
        var noReason = new LinkedHashMap<String, Object>();
        noReason.put("parameters", List.of(row("FULL_PAYMENT")));
        CatalogHttpTest.assertField(exchange(change(noReason, ADMIN), 400), "reason");
        var hours = row("FULL_PAYMENT"); hours.put("initialWaitHours", 0);
        CatalogHttpTest.assertField(exchange(change(Map.of("reason", "r", "parameters", List.of(hours)), ADMIN), 400), "initialWaitHours");
        var noMinimum = row("DEPOSIT"); noMinimum.remove("minimumDepositBp");
        CatalogHttpTest.assertField(exchange(change(Map.of("reason", "r", "parameters", List.of(noMinimum)), ADMIN), 400), "minimumDepositBp");
        CatalogHttpTest.assertField(exchange(change(Map.of("reason", "r", "parameters", List.of(row("FULL_PAYMENT")), "penalty", 5), ADMIN), 400), "penalty");
        CatalogHttpTest.assertField(exchange(change(Map.of("reason", "r", "parameters", List.of()), ADMIN), 400), "parameters");
        assertThat(payments.parameters).isEmpty();
    }

    // The contract requires Idempotency-Key on the change (norm 5.3.8): a missing or out-of-range key is a 400 that names the header.
    @Test void theChangeRequiresAnIdempotencyKeyOfEightToOneHundredTwentyEightCharacters() throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("reason", "New season"); body.put("parameters", List.of(row("FULL_PAYMENT")));

        CatalogHttpTest.assertField(exchange(withToken(put(BASE + "/payment-parameters"), ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body)), 400), "Idempotency-Key");
        CatalogHttpTest.assertField(exchange(change(body, ADMIN, "short"), 400), "Idempotency-Key");
        CatalogHttpTest.assertField(exchange(change(body, ADMIN, "k".repeat(129)), 400), "Idempotency-Key");
        assertThat(payments.parameters).isEmpty();
        exchange(change(body, ADMIN, "k".repeat(8)), 200);
        exchange(change(body, ADMIN, "k".repeat(128)), 200);
    }

    // The repetition answers and writes nothing; the same key with another body is refused. The repetition returns the rows in force now.
    @Test void theSameKeyAndBodyAnswersWithoutWritingAgainAndAnotherBodyIsRefused() throws Exception {
        var first = new LinkedHashMap<String, Object>();
        first.put("reason", "New season"); first.put("parameters", List.of(row("DEPOSIT"), row("FULL_PAYMENT")));
        var other = new LinkedHashMap<String, Object>();
        other.put("reason", "Another reason"); other.put("parameters", List.of(row("FULL_PAYMENT")));

        var original = CatalogHttpTest.json(exchange(change(first, ADMIN, "param-key-same-1"), 200));
        int written = payments.parameters.size();
        var replay = CatalogHttpTest.json(exchange(change(first, ADMIN, "param-key-same-1"), 200));

        assertThat(replay).isEqualTo(original);
        assertThat(payments.parameters).hasSize(written);
        var refused = CatalogHttpTest.json(exchange(change(other, ADMIN, "param-key-same-1"), 422));
        assertThat(refused.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(payments.parameters).hasSize(written);
    }
}
