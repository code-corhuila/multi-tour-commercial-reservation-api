package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// api spec 022 T-01 to T-03 (RN-SC-01 to RN-SC-06): the route receivePlatformEvent and the filter that refuses a token issued before the cutoff of its account. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, AccountSessionCutoffHttpTest.Support.class})
class AccountSessionCutoffHttpTest {
    static final String ROUTE = "/api/v1/commercial-reservation/internal/events";
    static final String PROBE = "/test-support/cutoff-probe";
    static final UUID ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT);
    static final String CUTOFF = "2026-10-09T11:00:00Z";
    @Autowired MockMvc mvc;
    @Autowired TenantEventHttpFixture.MemoryTenants store;
    @Autowired AccountSessionCutoffHttpFixture.MemoryCutoffs cutoffs;

    @TestConfiguration
    static class Support {
        @Bean ProbeController cutoffProbe() { return new ProbeController(); }
    }

    @TestComponent
    @RestController
    static class ProbeController {
        @GetMapping(PROBE) Map<String, String> probe() { return Map.of("status", "ok"); }
    }

    @BeforeEach void clear() { store.clear(); cutoffs.clear(); }

    static Map<String, Object> event(UUID id, UUID account, String from) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", account.toString());
        payload.put("revokedFrom", from);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", id.toString()); body.put("eventType", "AccountSessionsRevoked"); body.put("eventVersion", 1);
        body.put("aggregateId", account.toString()); body.put("payload", payload); body.put("occurredAt", from);
        return body;
    }

    static String token(String sub, List<String> roles, Instant issuedAt) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("sub", sub);
        claims.put("roles", roles);
        if (issuedAt != null) claims.put("iat", issuedAt.getEpochSecond());
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    static String person(Instant issuedAt) throws Exception { return token(ACCOUNT.toString(), List.of("TENANT_ADMINISTRATOR"), issuedAt); }

    MockHttpServletRequestBuilder call(Object body, String key, String bearer) throws Exception {
        var request = post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String text ? text : TestJwtKeys.JSON.writeValueAsString(body));
        if (key != null) request.header("Idempotency-Key", key);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return request;
    }

    void deliver(UUID id, UUID account, String from, int expected) throws Exception {
        mvc.perform(call(event(id, account, from), id.toString(), token("worker-event-relay", List.of("SERVICE"), null))).andExpect(status().is(expected));
    }

    int probe(String bearer) throws Exception {
        var request = get(PROBE);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test void aTokenIssuedBeforeTheCutoffIs401() throws Exception {
        deliver(UUID.randomUUID(), ACCOUNT, CUTOFF, 201);
        assertThat(probe(person(Instant.parse("2026-10-09T10:59:59Z")))).isEqualTo(401);
    }

    @Test void aTokenIssuedAfterTheCutoffIs200() throws Exception {
        deliver(UUID.randomUUID(), ACCOUNT, CUTOFF, 201);
        assertThat(probe(person(Instant.parse("2026-10-09T11:00:01Z")))).isEqualTo(200);
    }

    @Test void aTokenIssuedInTheSecondOfTheCutoffIs200() throws Exception {
        deliver(UUID.randomUUID(), ACCOUNT, "2026-10-09T11:00:00.900Z", 201);
        assertThat(probe(person(Instant.parse(CUTOFF)))).isEqualTo(200);
    }

    @Test void anAccountWithNoCutoffPasses() throws Exception {
        deliver(UUID.randomUUID(), UUID.randomUUID(), CUTOFF, 201);
        assertThat(probe(person(Instant.parse("2026-10-09T09:00:00Z")))).isEqualTo(200);
    }

    @Test void aTokenWithNoIatIsRefusedOnlyWithACutoff() throws Exception {
        assertThat(probe(person(null))).isEqualTo(200);
        deliver(UUID.randomUUID(), ACCOUNT, CUTOFF, 201);
        assertThat(probe(person(null))).isEqualTo(401);
    }

    @Test void aServiceTokenIsNotAffected() throws Exception {
        deliver(UUID.randomUUID(), ACCOUNT, CUTOFF, 201);
        assertThat(probe(token("worker-event-relay", List.of("SERVICE"), Instant.parse("2026-10-09T09:00:00Z")))).isEqualTo(200);
        assertThat(probe(token(ACCOUNT.toString(), List.of("SERVICE"), Instant.parse("2026-10-09T09:00:00Z")))).isEqualTo(200);
    }

    @Test void aFailedCutoffLookupIsNot200() throws Exception {
        cutoffs.failure = new IllegalStateException("the database is down");
        assertThat(probe(person(Instant.parse("2026-10-09T11:30:00Z")))).isEqualTo(500);
    }

    @Test void aRepeatedEventIs200AndAnOlderCutoffNeverMovesBack() throws Exception {
        UUID id = UUID.randomUUID();
        deliver(id, ACCOUNT, CUTOFF, 201);
        deliver(id, ACCOUNT, "2026-10-09T11:30:00Z", 200);
        deliver(UUID.randomUUID(), ACCOUNT, "2026-10-09T10:00:00Z", 200);
        assertThat(cutoffs.cutoffs.get(ACCOUNT).revokedFrom()).isEqualTo(Instant.parse(CUTOFF));
        assertThat(store.processed).hasSize(2);
        deliver(UUID.randomUUID(), ACCOUNT, "2026-10-09T11:30:00Z", 201);
        assertThat(cutoffs.cutoffs.get(ACCOUNT).revokedFrom()).isEqualTo(Instant.parse("2026-10-09T11:30:00Z"));
    }

    @Test void onlyTheServiceRoleReceivesTheEvent() throws Exception {
        UUID id = UUID.randomUUID();
        var body = event(id, ACCOUNT, CUTOFF);
        mvc.perform(call(body, id.toString(), null)).andExpect(status().isUnauthorized());
        mvc.perform(call(body, id.toString(), token("someone", List.of("TENANT_ADMINISTRATOR"), null))).andExpect(status().isForbidden());
        mvc.perform(call(body, id.toString(), token("someone", List.of("PLATFORM_ADMINISTRATOR"), null))).andExpect(status().isForbidden());
        assertThat(cutoffs.cutoffs).isEmpty();
    }

    @Test void theBodyIsValidatedAndTheTenantIsOptional() throws Exception {
        String bearer = token("worker-event-relay", List.of("SERVICE"), null);
        UUID id = UUID.randomUUID();
        var good = event(id, ACCOUNT, CUTOFF);
        mvc.perform(call(good, null, bearer)).andExpect(status().isBadRequest());
        mvc.perform(call(good, UUID.randomUUID().toString(), bearer)).andExpect(status().isBadRequest());
        mvc.perform(call("{not json", id.toString(), bearer)).andExpect(status().isBadRequest());
        var otherType = event(id, ACCOUNT, CUTOFF);
        otherType.put("eventType", "TenantCreated");
        mvc.perform(call(otherType, id.toString(), bearer)).andExpect(status().isBadRequest());
        var otherAggregate = event(id, ACCOUNT, CUTOFF);
        otherAggregate.put("aggregateId", UUID.randomUUID().toString());
        mvc.perform(call(otherAggregate, id.toString(), bearer)).andExpect(status().isBadRequest());
        var badDate = event(id, ACCOUNT, "yesterday");
        mvc.perform(call(badDate, id.toString(), bearer)).andExpect(status().isBadRequest());
        var noAccount = event(id, ACCOUNT, CUTOFF);
        ((Map<String, Object>) noAccount.get("payload")).remove("accountId");
        mvc.perform(call(noAccount, id.toString(), bearer)).andExpect(status().isBadRequest());
        mvc.perform(call(good, id.toString(), bearer).contentType(MediaType.TEXT_PLAIN)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(call("{\"pad\":\"" + "x".repeat(16 * 1024) + "\"}", id.toString(), bearer)).andExpect(status().is(413));
        assertThat(cutoffs.cutoffs).isEmpty();
        assertThat(store.processed).isEmpty();

        ((Map<String, Object>) good.get("payload")).put("tenantId", TestJwtKeys.TENANT);
        mvc.perform(call(good, id.toString(), bearer)).andExpect(status().isCreated());
        assertThat(cutoffs.cutoffs.get(ACCOUNT).tenantId()).hasToString(TestJwtKeys.TENANT);
        UUID second = UUID.randomUUID();
        var withoutTenant = event(second, ACCOUNT, "2026-10-09T11:10:00Z");
        mvc.perform(call(withoutTenant, second.toString(), bearer)).andExpect(status().isCreated());
        UUID third = UUID.randomUUID();
        var otherTenant = event(third, ACCOUNT, "2026-10-09T11:20:00Z");
        ((Map<String, Object>) otherTenant.get("payload")).put("tenantId", UUID.randomUUID().toString());
        mvc.perform(call(otherTenant, third.toString(), bearer)).andExpect(status().isBadRequest());
        assertThat(store.processed).doesNotContain(third);
    }
}
