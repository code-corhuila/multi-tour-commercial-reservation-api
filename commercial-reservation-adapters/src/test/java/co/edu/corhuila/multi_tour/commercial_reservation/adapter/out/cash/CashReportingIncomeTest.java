package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CashUnavailableException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// HU-COM-009, AC-09.2 (decided 2026-11-12): the cash income goes straight to the internal route of the cash api with the service
// token and a stable Idempotency-Key. A fake cash server stands for the cash api; nothing here is a real secret.
class CashReportingIncomeTest {

    private static final UUID TENANT = UUID.fromString("6f1c1c64-0000-4000-8000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("6f1c1c64-0000-4000-8000-000000000002");
    private static final UUID PERSON = UUID.fromString("6f1c1c64-0000-4000-8000-000000000003");
    private static final UUID MOVEMENT = UUID.fromString("6f1c1c64-0000-4000-8000-000000000004");
    private static final String DAY = "{\"status\":\"OPEN\",\"hasOperationalOpening\":true,\"businessDate\":\"2026-11-12\"}";
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Call(String method, String path, String authorization, String key, String body) {}

    private HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile int dayStatus = 200;
    private volatile String dayBody = DAY;
    private volatile int postStatus = 201;
    private volatile String postBody = "{\"cashMovementId\":\"" + MOVEMENT + "\"}";
    private volatile long delayMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("Idempotency-Key"), body));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            boolean read = "GET".equals(exchange.getRequestMethod());
            byte[] bytes = (read ? dayBody : postBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(read ? dayStatus : postStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private CashReportingIncome adapter(String personToken, String serviceToken, long timeoutMillis) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        Duration timeout = Duration.ofMillis(timeoutMillis);
        return new CashReportingIncome(base, serviceToken, new CashReportingSessionStatus(base, () -> Optional.ofNullable(personToken), timeout), timeout);
    }

    private CashReportingIncome adapter() {
        return adapter("person-token", "service-token", 2_000);
    }

    private List<Call> posts() {
        return new ArrayList<>(calls.stream().filter(call -> call.method().equals("POST")).toList());
    }

    @Test
    void theIncomeGoesToTheInternalRouteOfTheTenantWithTheServiceTokenAndTheBodyOfTheContract() throws Exception {
        UUID movement = adapter().record(TENANT, RESERVATION, "request-key-0001:100000", new Cents(100_000), PERSON);

        assertThat(movement).isEqualTo(MOVEMENT);
        assertThat(calls.get(0)).satisfies(read -> {
            assertThat(read.path()).isEqualTo("/api/v1/cash-reporting/cash-days/current");
            assertThat(read.authorization()).isEqualTo("Bearer person-token");
        });
        Call write = posts().get(0);
        assertThat(write.path()).isEqualTo("/api/v1/cash-reporting/internal/tenants/" + TENANT + "/cash-movements");
        assertThat(write.authorization()).isEqualTo("Bearer service-token");
        assertThat(write.key()).hasSize(36);
        JsonNode body = JSON.readTree(write.body());
        assertThat(body.path("businessDate").asText()).isEqualTo("2026-11-12");
        assertThat(body.path("paymentMedium").asText()).isEqualTo("CASH");
        assertThat(body.path("amountCents").asLong()).isEqualTo(100_000L);
        assertThat(body.path("reservationId").asText()).isEqualTo(RESERVATION.toString());
        assertThat(body.path("onBehalfOf").asText()).isEqualTo(PERSON.toString());
        assertThat(body.path("concept").asText()).isNotBlank();
    }

    @Test
    void theSameIntentionSendsTheSameKeyAndAnotherIntentionAnotherOne() {
        CashReportingIncome income = adapter();
        income.record(TENANT, RESERVATION, "request-key-0001:100000", new Cents(100_000), PERSON);
        income.record(TENANT, RESERVATION, "request-key-0001:100000", new Cents(100_000), PERSON);
        income.record(TENANT, RESERVATION, "request-key-0002:100000", new Cents(100_000), PERSON);

        List<Call> writes = posts();
        assertThat(writes.get(0).key()).isEqualTo(writes.get(1).key()).isNotEqualTo(writes.get(2).key());
    }

    @Test
    void aReplayOfTheKeyAnswers200AndReturnsTheMovementAlreadyRecorded() {
        postStatus = 200;

        assertThat(adapter().record(TENANT, RESERVATION, "request-key-0001:100000", new Cents(100_000), PERSON)).isEqualTo(MOVEMENT);
    }

    @Test
    void aRefusalOfTheCashIsABusinessRuleAndNothingIsRetried() {
        postStatus = 422;
        postBody = "{\"error\":\"NO_OPEN_CASH\"}";

        assertThatThrownBy(() -> adapter().record(TENANT, RESERVATION, "k", new Cents(1), PERSON)).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("NO_OPEN_CASH");
        assertThat(posts()).hasSize(1);
    }

    @Test
    void aClosedDayIsRefusedBeforeAnythingIsWritten() {
        dayBody = "{\"status\":\"CLOSED\",\"hasOperationalOpening\":true,\"businessDate\":\"2026-11-12\"}";

        assertThatThrownBy(() -> adapter().record(TENANT, RESERVATION, "k", new Cents(1), PERSON)).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("cash session");
        assertThat(posts()).isEmpty();
    }

    @Test
    void aFailureOfTheCashIsUnavailableNotARefusal() {
        for (int status : new int[] {400, 401, 403, 404, 500, 503}) {
            postStatus = status;
            assertThatThrownBy(() -> adapter().record(TENANT, RESERVATION, "k", new Cents(1), PERSON))
                    .as("status %d", status).isInstanceOf(CashUnavailableException.class);
        }
    }

    @Test
    void anAnswerWithoutAMovementIdOrWithUnreadableBodyIsUnavailable() {
        postBody = "{}";
        assertThatThrownBy(() -> adapter().record(TENANT, RESERVATION, "k", new Cents(1), PERSON)).isInstanceOf(CashUnavailableException.class);
        postBody = "not json";
        assertThatThrownBy(() -> adapter().record(TENANT, RESERVATION, "k", new Cents(1), PERSON)).isInstanceOf(CashUnavailableException.class);
    }

    @Test
    void aSlowCashIsUnavailableAfterTheTimeout() {
        delayMillis = 600;

        assertThatThrownBy(() -> adapter("person-token", "service-token", 150).record(TENANT, RESERVATION, "k", new Cents(1), PERSON))
                .isInstanceOf(CashUnavailableException.class);
    }

    @Test
    void aCashThatCannotBeReachedIsUnavailable() {
        server.stop(0);

        assertThatThrownBy(() -> adapter().record(TENANT, RESERVATION, "k", new Cents(1), PERSON)).isInstanceOf(CashUnavailableException.class);
    }

    @Test
    void withoutAServiceTokenNothingIsSentToTheInternalRoute() {
        for (String token : new String[] {null, "", "  "}) {
            assertThatThrownBy(() -> adapter("person-token", token, 2_000).record(TENANT, RESERVATION, "k", new Cents(1), PERSON))
                    .isInstanceOf(CashUnavailableException.class).hasMessageContaining("not configured");
        }
        assertThat(posts()).isEmpty();
    }

    @Test
    void withoutThePersonTokenTheDayCannotBeReadAndNothingIsWritten() {
        assertThatThrownBy(() -> adapter(null, "service-token", 2_000).record(TENANT, RESERVATION, "k", new Cents(1), PERSON))
                .isInstanceOf(CashUnavailableException.class);
        assertThat(posts()).isEmpty();
    }

    @Test
    void theReversalGoesToTheRouteOfTheMovementWithAKeyOfItsOwnAndTheSameKeyEachTime() throws Exception {
        CashReportingIncome income = adapter();
        income.reverse(TENANT, MOVEMENT);
        income.reverse(TENANT, MOVEMENT);

        List<Call> writes = posts();
        assertThat(writes.get(0).path()).isEqualTo("/api/v1/cash-reporting/internal/tenants/" + TENANT + "/cash-movements/" + MOVEMENT + "/reversals");
        assertThat(writes.get(0).authorization()).isEqualTo("Bearer service-token");
        assertThat(writes.get(0).key()).isEqualTo(writes.get(1).key());
        assertThat(JSON.readTree(writes.get(0).body()).path("reason").asText()).isNotBlank();
    }

    @Test
    void aReversalThatFailsIsReportedSoTheCallerCanKeepTheOriginalError() {
        postStatus = 500;

        assertThatThrownBy(() -> adapter().reverse(TENANT, MOVEMENT)).isInstanceOf(CashUnavailableException.class);
    }
}
