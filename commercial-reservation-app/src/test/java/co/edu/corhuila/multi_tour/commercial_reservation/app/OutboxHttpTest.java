package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OutboxDeliveryStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-015 api T-06 and T-07 over the three outbox routes of the contract (AC-15.6, AC-15.7, RN-09): only the SERVICE token of job 7 calls them, a
// person gets 403, the answers carry the envelope and nothing personal, a repeat answers the same state. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, OutboxHttpTest.Memory.class})
class OutboxHttpTest {
    static final String BASE = "/api/v1/commercial-reservation/internal/outbox";
    static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    @TestConfiguration
    static class Memory {
        @Bean @Primary MemoryOutbox memoryOutbox() { return new MemoryOutbox(); }
    }

    static final class MemoryOutbox implements OutboxDeliveryStore {
        final Map<UUID, OutboxEventView> events = new LinkedHashMap<>();
        public OutboxEventPage pending(Instant now, int page, int limit) {
            List<OutboxEventView> all = events.values().stream().filter(found -> found.status() == OutboxStatus.PENDING).toList();
            int from = Math.min((page - 1) * limit, all.size());
            return new OutboxEventPage(all.subList(from, Math.min(from + limit, all.size())), all.size());
        }
        public Optional<OutboxEventView> find(UUID eventId) { return Optional.ofNullable(events.get(eventId)); }
        public boolean markPublished(UUID eventId, Instant now) {
            OutboxEventView found = events.get(eventId);
            if (found == null || found.status() != OutboxStatus.PENDING) return false;
            events.put(eventId, new OutboxEventView(found.eventId(), found.eventType(), found.eventVersion(), found.tenantId(), found.aggregateId(),
                    found.payload(), found.correlationId(), found.occurredAt(), now, OutboxStatus.PUBLISHED, found.attempts(), found.lastError(), null));
            return true;
        }
        public boolean recordAttempt(UUID eventId, int expectedAttempts, String lastError, Instant nextAttemptAt, Instant failedAt) {
            OutboxEventView found = events.get(eventId);
            if (found == null || found.status() != OutboxStatus.PENDING || found.attempts() != expectedAttempts) return false;
            events.put(eventId, new OutboxEventView(found.eventId(), found.eventType(), found.eventVersion(), found.tenantId(), found.aggregateId(),
                    found.payload(), found.correlationId(), found.occurredAt(), null, failedAt == null ? OutboxStatus.PENDING : OutboxStatus.FAILED,
                    expectedAttempts + 1, lastError, nextAttemptAt));
            return true;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MemoryOutbox outbox;
    UUID event;

    @BeforeEach void plant() {
        outbox.events.clear();
        event = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("customerId", UUID.randomUUID().toString());
        payload.put("notificationType", "PROOF_APPROVED");
        payload.put("triggeringEventType", "PaymentValidated");
        payload.put("triggeringEventId", UUID.randomUUID().toString());
        outbox.events.put(event, new OutboxEventView(event, "ClientNotified", 1, UUID.randomUUID(), UUID.randomUUID(), payload, "trace-1",
                NOW.minusSeconds(60), null, OutboxStatus.PENDING, 0, null, null));
    }

    private String token(String role) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of(role));
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String role) throws Exception {
        request.header("X-Correlation-Id", "outbox-test");
        return role == null ? request : request.header("Authorization", "Bearer " + token(role));
    }

    private MockHttpServletRequestBuilder attempt(String body, String role) throws Exception {
        return as(post(BASE + "/" + event + "/attempts").contentType(MediaType.APPLICATION_JSON).content(body), role);
    }

    @Test void job7ReadsThePendingEventsWithTheEnvelopeAndOnlyWithAServiceToken() throws Exception {
        mvc.perform(as(get(BASE + "?status=PENDING&limit=10"), "SERVICE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventId").value(event.toString()))
                .andExpect(jsonPath("$.data[0].eventType").value("ClientNotified"))
                .andExpect(jsonPath("$.data[0].eventVersion").value(1))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].attempts").value(0))
                .andExpect(jsonPath("$.data[0].payload.notificationType").value("PROOF_APPROVED"))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.limit").value(10));
        mvc.perform(as(get(BASE), "TENANT_ADMINISTRATOR")).andExpect(status().isForbidden());
        mvc.perform(as(get(BASE), null)).andExpect(status().isUnauthorized());
        mvc.perform(as(get(BASE + "?limit=101"), "SERVICE")).andExpect(status().isBadRequest());
        mvc.perform(as(get(BASE + "?status=FAILED"), "SERVICE")).andExpect(status().isBadRequest());
    }

    @Test void markingPublishedTwiceAnswersOkWithTheSameState() throws Exception {
        MockHttpServletRequestBuilder first = as(post(BASE + "/" + event + "/published"), "SERVICE");
        mvc.perform(first).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(as(post(BASE + "/" + event + "/published"), "SERVICE")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(as(post(BASE + "/" + event + "/published"), "OPERATIONAL_COLLABORATOR")).andExpect(status().isForbidden());
        mvc.perform(as(post(BASE + "/" + UUID.randomUUID() + "/published"), "SERVICE")).andExpect(status().isNotFound());
    }

    @Test void anAttemptRecordsTheCountTheErrorAndTheNextWait() throws Exception {
        mvc.perform(attempt("{\"error\":\"The mail provider answered 503\"}", "SERVICE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.lastError").value("The mail provider answered 503"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.nextAttemptAt").exists());
        mvc.perform(attempt("{\"error\":\"x\"}", "TENANT_ADMINISTRATOR")).andExpect(status().isForbidden());
        mvc.perform(attempt("{}", "SERVICE")).andExpect(status().isBadRequest());
        mvc.perform(attempt("{\"error\":\"\"}", "SERVICE")).andExpect(status().isBadRequest());
        mvc.perform(attempt("{\"error\":\"" + "x".repeat(1001) + "\"}", "SERVICE")).andExpect(status().isBadRequest());
        mvc.perform(attempt("{\"error\":\"x\",\"extra\":1}", "SERVICE")).andExpect(status().isBadRequest());
        mvc.perform(as(post(BASE + "/" + UUID.randomUUID() + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{\"error\":\"x\"}"), "SERVICE"))
                .andExpect(status().isNotFound());
    }
}
