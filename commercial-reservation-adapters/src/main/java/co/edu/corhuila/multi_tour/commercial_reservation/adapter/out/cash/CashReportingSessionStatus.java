package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashSessionStatus;
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
        Optional<String> token = bearer.get().filter(text -> !text.isBlank());
        if (token.isEmpty()) {
            return false;
        }
        HttpRequest request = HttpRequest.newBuilder(current)
                .timeout(timeout)
                .header("Authorization", "Bearer " + token.get().trim())
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && opened(JSON.readTree(response.body()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static boolean opened(JsonNode day) {
        return day != null
                && "OPEN".equals(day.path("status").asText())
                && day.path("hasOperationalOpening").isBoolean()
                && day.path("hasOperationalOpening").booleanValue();
    }
}
