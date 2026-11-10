package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// P1 of the proposal of the cash internal routes (HU-COM-009): "is the cash open?" reads the existing route of the cash
// api forwarding the token of the person who pays. It fails closed: anything but a clear "open with operational opening"
// is "not verified". A fake cash server stands for the cash api.
class CashReportingSessionStatusTest {

    private static final String PATH = "/api/v1/cash-reporting/cash-days/current";
    private static final UUID TENANT = UUID.fromString("6f1c1c64-0000-4000-8000-000000000001");

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "{}";
    private volatile long delayMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private CashReportingSessionStatus adapter(String token) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new CashReportingSessionStatus(base, () -> Optional.ofNullable(token), Duration.ofMillis(500));
    }

    private void cash(String state, boolean opened) {
        body = "{\"status\":\"" + state + "\",\"hasOperationalOpening\":" + opened + "}";
    }

    @Test
    void anOpenCashWithOperationalOpeningIsOpenAndTheTokenOfThePersonIsForwarded() {
        cash("OPEN", true);

        assertThat(adapter("person-token").isOpen(TENANT)).isTrue();

        assertThat(path.get()).isEqualTo(PATH);
        assertThat(authorization.get()).isEqualTo("Bearer person-token");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void anOpenCashWithoutOperationalOpeningIsNotOpenForCash() {
        cash("OPEN", false);

        assertThat(adapter("person-token").isOpen(TENANT)).isFalse();
    }

    @Test
    void aClosedCashAndADayWithoutOpeningAreNotOpen() {
        cash("CLOSED", true);
        assertThat(adapter("person-token").isOpen(TENANT)).isFalse();

        cash("NO_OPENING", false);
        assertThat(adapter("person-token").isOpen(TENANT)).isFalse();
    }

    @Test
    void aForbiddenAnswerOrAnyServerErrorMeansNotVerified() {
        status = 403;
        assertThat(adapter("person-token").isOpen(TENANT)).isFalse();

        status = 401;
        assertThat(adapter("person-token").isOpen(TENANT)).isFalse();

        status = 500;
        cash("OPEN", true);
        assertThat(adapter("person-token").isOpen(TENANT)).isFalse();
    }

    @Test
    void anUnreadableAnswerMeansNotVerified() {
        body = "not json";
        assertThat(adapter("person-token").isOpen(TENANT)).isFalse();

        body = "{\"status\":\"OPEN\"}";
        assertThat(adapter("person-token").isOpen(TENANT)).isFalse();

        body = "[]";
        assertThat(adapter("person-token").isOpen(TENANT)).isFalse();
    }

    @Test
    void withoutTheTokenOfAPersonTheCashApiIsNotCalledAndTheAnswerIsNotVerified() {
        cash("OPEN", true);

        assertThat(adapter(null).isOpen(TENANT)).isFalse();
        assertThat(adapter("  ").isOpen(TENANT)).isFalse();

        assertThat(calls.get()).isZero();
    }

    @Test
    void aSlowCashApiMeansNotVerified() {
        cash("OPEN", true);
        delayMillis = 2_000;

        assertThat(adapter("person-token").isOpen(TENANT)).isFalse();
    }

    @Test
    void aCashApiThatIsDownMeansNotVerified() {
        cash("OPEN", true);
        CashReportingSessionStatus adapter = adapter("person-token");
        server.stop(0);

        assertThat(adapter.isOpen(TENANT)).isFalse();
    }

    @Test
    void theRecordingAndTheReversalOfTheIncomeStayRefusedWithAnExplicitMessage() {
        UnverifiedCashSession standIn = new UnverifiedCashSession();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> standIn.record(TENANT, TENANT, null, TENANT))
                .hasMessageContaining("cannot be recorded");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> standIn.reverse(TENANT, TENANT))
                .hasMessageContaining("cannot be reversed");
    }
}
