package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExecutionChange;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ChangeDepartureExecution;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// G-API4-1 over HTTP (RN-DEX-05, RN-DEX-06): the routes operations-cost calls when a departure starts or ends. The use case is a fake that records the call; its
// rules are proved in ChangeDepartureExecutionUseCaseTest and the real statements in DepartureExecutionPersistenceTest. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, DepartureExecutionHttpTest.Fixture.class})
class DepartureExecutionHttpTest {
    @TestConfiguration
    static class Fixture {
        @Bean @Primary RecordingExecution recordingExecution() { return new RecordingExecution(); }
    }

    static final class RecordingExecution implements ChangeDepartureExecution {
        final List<List<Object>> calls = new ArrayList<>();
        RuntimeException failure;

        @Override public ExecutionChange start(UUID tenantId, UUID salidaId, String key) { return call("start", tenantId, salidaId, key); }
        @Override public ExecutionChange end(UUID tenantId, UUID salidaId, String key) { return call("end", tenantId, salidaId, key); }

        private ExecutionChange call(String kind, UUID tenantId, UUID salidaId, String key) {
            calls.add(List.of(kind, tenantId, salidaId, key));
            if (failure != null) throw failure;
            return new ExecutionChange(salidaId, 2, 1);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired RecordingExecution execution;
    final UUID tenant = UUID.randomUUID(), departure = UUID.randomUUID();

    @BeforeEach void clear() { execution.calls.clear(); execution.failure = null; }

    MvcResult call(String route, String role, String key, int code) throws Exception {
        var request = post("/api/v1/commercial-reservation/internal/tenants/" + tenant + "/departures/" + departure + "/" + route)
                .header("X-Correlation-Id", CatalogHttpTest.TRACE);
        if (role != null) request.header("Authorization", "Bearer " + CustomerHttpTest.token(null, role, List.of()));
        if (key != null) request.header("Idempotency-Key", key);
        MvcResult result = mvc.perform(request).andReturn();
        status().is(code).match(result);
        return result;
    }

    @Test void theServiceStartsAndEndsADepartureAndGetsOnlyTheCounts() throws Exception {
        JsonNode started = CatalogHttpTest.json(call("execution-start", "SERVICE", "execution-key-0001", 200));
        assertThat(started.size()).isEqualTo(3);
        assertThat(started.get("departureId").asText()).isEqualTo(departure.toString());
        assertThat(started.get("changed").asInt()).isEqualTo(2);
        assertThat(started.get("unchanged").asInt()).isEqualTo(1);
        call("execution-end", "SERVICE", "execution-key-0002", 200);
        assertThat(execution.calls).containsExactly(List.of("start", tenant, departure, "execution-key-0001"), List.of("end", tenant, departure, "execution-key-0002"));
    }

    @Test void aPersonGets403NoTokenIs401AndNothingIsCalled() throws Exception {
        for (String route : List.of("execution-start", "execution-end")) {
            call(route, "TENANT_ADMINISTRATOR", "execution-key-0001", 403);
            call(route, "OPERATIONAL_COLLABORATOR", "execution-key-0001", 403);
            call(route, null, "execution-key-0001", 401);
        }
        assertThat(execution.calls).isEmpty();
    }

    @Test void theKeyIsRequiredAndBounded() throws Exception {
        call("execution-start", "SERVICE", null, 400);
        call("execution-end", "SERVICE", "short", 400);
        call("execution-start", "SERVICE", "k".repeat(129), 400);
        assertThat(execution.calls).isEmpty();
    }

    @Test void anUnknownDepartureIs404AndAFinalOneOrAReusedKeyIs422() throws Exception {
        execution.failure = new DepartureNotFoundException(departure);
        call("execution-start", "SERVICE", "execution-key-0001", 404);
        execution.failure = new BusinessRuleViolation("the departure is final and cannot start");
        call("execution-start", "SERVICE", "execution-key-0001", 422);
        call("execution-end", "SERVICE", "execution-key-0001", 422);
    }
}
