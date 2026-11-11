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
        return withToken(put(BASE + "/payment-parameters"), role).contentType(MediaType.APPLICATION_JSON)
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

    @Test void onlyTheAdministratorChangesAndTheFinalCustomerReadsNothing() throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("reason", "New season"); body.put("parameters", List.of(row("FULL_PAYMENT")));
        for (String role : List.of(COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER)) exchange(change(body, role), 403);
        exchange(withToken(get(BASE + "/payment-parameters"), FINAL_CUSTOMER), 403);
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
}
