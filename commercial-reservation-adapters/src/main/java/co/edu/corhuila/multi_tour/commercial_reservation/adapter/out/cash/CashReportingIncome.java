package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashIncomeRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CashUnavailableException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * The cash income of a cash payment, recorded directly in the cash (HU-COM-009, decided 2026-11-12, PROPOSED): the internal route of
 * the cash api with the SERVICE token of this service, because the saga register-payment cannot start a cash payment (it needs an
 * existing paymentId). The business date is the one of the open cash day, read with the token of the person who pays. The
 * Idempotency-Key is derived from the intention, so a retry replays the movement (200) and never records a second income. A refusal of
 * the cash (422) is a business rule; anything else (no answer, timeout, 5xx, a token the cash rejects, no token configured) is
 * CashUnavailableException: the income is unknown and the payment is not registered. The token is never logged.
 */
public final class CashReportingIncome implements CashIncomeRecorder {

    private static final String INCOME = "/api/v1/cash-reporting/internal/tenants/%s/cash-movements";
    private static final String REVERSAL = "/api/v1/cash-reporting/internal/tenants/%s/cash-movements/%s/reversals";
    private static final String CONCEPT = "Pago en efectivo de la reserva";
    private static final String REASON = "The reservation step failed after the cash income was recorded";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String base;
    private final String serviceToken;
    private final CashReportingSessionStatus day;
    private final Duration timeout;
    private final HttpClient client;

    public CashReportingIncome(String baseUrl, String serviceToken, CashReportingSessionStatus day, Duration timeout) {
        this.base = baseUrl.replaceAll("/+$", "");
        this.serviceToken = serviceToken == null ? "" : serviceToken.trim();
        this.day = day;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public UUID record(UUID tenantId, UUID reservationId, String intentKey, Cents amount, UUID registeredBy) {
        String businessDate = day.openBusinessDate();
        ObjectNode body = JSON.createObjectNode().put("businessDate", businessDate).put("paymentMedium", "CASH")
                .put("amountCents", amount.amount()).put("reservationId", reservationId.toString()).put("concept", CONCEPT);
        if (registeredBy != null) body.put("onBehalfOf", registeredBy.toString());
        JsonNode movement = post(INCOME.formatted(tenantId), key("income", tenantId, reservationId, intentKey), body);
        String id = movement.path("cashMovementId").asText("");
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException unclear) {
            throw new CashUnavailableException("the cash answered without a movement id");
        }
    }

    @Override
    public void reverse(UUID tenantId, UUID movementId) {
        post(REVERSAL.formatted(tenantId, movementId), key("reversal", tenantId, movementId, ""), JSON.createObjectNode().put("reason", REASON));
    }

    // One key per intention, private to the tenant: the same request gives the same key, another request another one.
    private static String key(String kind, UUID tenantId, UUID subject, String intent) {
        String text = "cash-" + kind + "|" + tenantId + "|" + subject + "|" + intent;
        return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private JsonNode post(String path, String idempotencyKey, ObjectNode body) {
        if (serviceToken.isEmpty()) {
            throw new CashUnavailableException("the service token of the cash is not configured");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", idempotencyKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CashUnavailableException("the call to the cash was interrupted", interrupted);
        } catch (IOException | RuntimeException failure) {
            throw new CashUnavailableException("the cash does not answer", failure);
        }
        int status = response.statusCode();
        if (status == 200 || status == 201) {
            try {
                return JSON.readTree(response.body());
            } catch (IOException unreadable) {
                throw new CashUnavailableException("the cash answer cannot be read", unreadable);
            }
        }
        if (status == 422) {
            throw new BusinessRuleViolation("the cash refused the income: " + codeOf(response.body()));
        }
        throw new CashUnavailableException("the cash answered " + status);
    }

    private static String codeOf(String body) {
        try {
            String code = JSON.readTree(body).path("error").asText("");
            return code.isBlank() ? "BUSINESS_RULE_VIOLATION" : code;
        } catch (IOException | RuntimeException unreadable) {
            return "BUSINESS_RULE_VIOLATION";
        }
    }
}
