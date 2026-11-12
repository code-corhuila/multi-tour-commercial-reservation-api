package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashSessionStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CashUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * "Is the cash open?" (RN-05) read from the cash api (ADR-004 D4 and D5): a domain api that reads another domain's data
 * during a person's request forwards that person's token unchanged, and the cash api takes the tenant from it. The cash is
 * open when its day is {@code OPEN} and has an operational opening. It fails closed: no token, a refusal, an error, a
 * timeout or an answer that is not clear mean "not verified", never "open". It only reads; writing in the cash goes
 * through the saga (proposal-internal-routes.md, P1).
 */
public final class CashReportingSessionStatus implements CashSessionStatus {

    private static final String CURRENT_DAY = "/api/v1/cash-reporting/cash-days/current";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI current;
    private final Supplier<Optional<String>> bearer;
    private final Duration timeout;
    private final HttpClient client;

    public CashReportingSessionStatus(String baseUrl, Supplier<Optional<String>> bearer, Duration timeout) {
        this.current = URI.create(baseUrl.replaceAll("/+$", "") + CURRENT_DAY);
        this.bearer = bearer;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public boolean isOpen(UUID tenantId) {
        try {
            return opened(readDay());
        } catch (RuntimeException notVerified) {
            return false;
        }
    }

    /**
     * The business date of the open cash day, for the income of a cash payment (HU-COM-009): a day that is not open with an
     * operational opening is a rule violation; anything that cannot be read is CashUnavailableException (the income is unknown).
     */
    String openBusinessDate() {
        JsonNode day = readDay();
        if (!opened(day)) {
            throw new BusinessRuleViolation("there is no open cash session");
        }
        String date = day.path("businessDate").asText("");
        if (date.isBlank()) {
            throw new CashUnavailableException("the cash day has no business date");
        }
        return date;
    }

    private JsonNode readDay() {
        Optional<String> token = bearer.get().filter(text -> !text.isBlank());
        if (token.isEmpty()) {
            throw new CashUnavailableException("the cash day cannot be read without the token of the person");
        }
        HttpRequest request = HttpRequest.newBuilder(current)
                .timeout(timeout)
                .header("Authorization", "Bearer " + token.get().trim())
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new CashUnavailableException("the cash answered " + response.statusCode());
            }
            return JSON.readTree(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CashUnavailableException("the call to the cash was interrupted", interrupted);
        } catch (IOException failure) {
            throw new CashUnavailableException("the cash does not answer", failure);
        }
    }

    private static boolean opened(JsonNode day) {
        return day != null
                && "OPEN".equals(day.path("status").asText())
                && day.path("hasOperationalOpening").isBoolean()
                && day.path("hasOperationalOpening").booleanValue();
    }
}
