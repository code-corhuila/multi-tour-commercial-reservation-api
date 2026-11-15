package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-004 over HTTP on the real schema: AC-04.9 roles, AC-04.11 idempotency, AC-04.15 list, add and disable.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = CatalogHttpPersistenceTest.Database.class)
@Import(CatalogHttpPersistenceTest.FixedClock.class)
class AuthorizableReasonHttpTest {
    static final String BASE = "/api/v1/commercial-reservation/additional-discount-reasons";
    static final String ADMIN = "TENANT_ADMINISTRATOR";
    static final String TRACE = CatalogHttpTest.TRACE;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    MvcResult exchange(MockHttpServletRequestBuilder request, int code) throws Exception {
        MvcResult result = mvc.perform(request.header("X-Correlation-Id", TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String tenant, String role)
            throws Exception {
        return request.header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, role));
    }

    static MockHttpServletRequestBuilder add(Object body, String key, String tenant, String role) throws Exception {
        var request = as(post(BASE), tenant, role).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
        if (key != null) request.header("Idempotency-Key", key);
        return request;
    }

    static MockHttpServletRequestBuilder disable(String id, String tenant, String role) throws Exception {
        return as(post(BASE + "/" + id + "/disable"), tenant, role);
    }

    JsonNode added(String tenant, String text) throws Exception {
        return CatalogHttpTest.json(exchange(add(Map.of("text", text), UUID.randomUUID().toString(), tenant, ADMIN), 201));
    }

    static void assertBusinessRule(MvcResult result) throws Exception {
        var error = CatalogHttpTest.json(result);
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.get("traceId").asText()).isEqualTo(TRACE);
    }

    long rows(String tenant) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.authorizable_reason WHERE tenant_id = ?",
                Long.class, UUID.fromString(tenant));
    }

    @Test void addingAReasonAnswers201WithItsLocationAndTheEnabledReason() throws Exception {
        String tenant = UUID.randomUUID().toString();
        var result = exchange(add(Map.of("text", "  Customer loyalty "), "reason-key-1", tenant, ADMIN), 201);
        var reason = CatalogHttpTest.json(result);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(BASE + "/" + reason.get("reasonId").asText());
        List<String> fields = new ArrayList<>(); reason.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("reasonId", "text", "reasonStatus", "createdAt", "updatedAt");
        assertThat(reason.get("text").asText()).isEqualTo("Customer loyalty");
        assertThat(reason.get("reasonStatus").asText()).isEqualTo("ENABLED");
        Instant.parse(reason.get("createdAt").asText()); Instant.parse(reason.get("updatedAt").asText());
        assertThat(rows(tenant)).isEqualTo(1);
    }

    @Test void repeatingTheKeyAnswers200WithTheSameReasonAndLeavesOneRow() throws Exception {
        String tenant = UUID.randomUUID().toString();
        var first = CatalogHttpTest.json(exchange(add(Map.of("text", "Group agreement"), "reason-key-2", tenant, ADMIN), 201));
        var second = CatalogHttpTest.json(exchange(add(Map.of("text", "Group agreement"), "reason-key-2", tenant, ADMIN), 200));
        assertThat(second).isEqualTo(first);
        assertThat(rows(tenant)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"", "short"})
    void aMissingOrShortKeyIsRefusedNamingTheHeader(String key) throws Exception {
        String tenant = UUID.randomUUID().toString();
        CatalogHttpTest.assertField(exchange(add(Map.of("text", "Any"), key.isEmpty() ? null : key, tenant, ADMIN), 400),
                "Idempotency-Key");
        assertThat(rows(tenant)).isZero();
    }

    @Test void anEmptyTextOrAnUnknownFieldIsRefusedNamingTheFieldAndWritesNothing() throws Exception {
        String tenant = UUID.randomUUID().toString();
        CatalogHttpTest.assertField(exchange(add(Map.of("text", "   "), "reason-key-3", tenant, ADMIN), 400), "text");
        CatalogHttpTest.assertField(exchange(add(Map.of(), "reason-key-4", tenant, ADMIN), 400), "text");
        CatalogHttpTest.assertField(exchange(add(Map.of("text", "x".repeat(256)), "reason-key-8", tenant, ADMIN), 400), "text");
        CatalogHttpTest.assertField(exchange(add(Map.of("text", "Valid", "tenantId", tenant), "reason-key-5", tenant, ADMIN), 400),
                "tenantId");
        assertThat(rows(tenant)).isZero();
    }

    @Test void aDuplicateTextOfTheSameTenantIsABusinessRuleViolationButAnotherTenantMayRepeatIt() throws Exception {
        String tenant = UUID.randomUUID().toString();
        added(tenant, "Customer loyalty");
        assertBusinessRule(exchange(add(Map.of("text", " CUSTOMER LOYALTY "), "reason-key-6", tenant, ADMIN), 422));
        assertThat(rows(tenant)).isEqualTo(1);
        added(UUID.randomUUID().toString(), "Customer loyalty");
    }

    @Test void listsEnabledAndDisabledReasonsOfTheTenantOnlyInPagesWithTheirMeta() throws Exception {
        String tenant = UUID.randomUUID().toString();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) ids.add(added(tenant, "Reason " + i).get("reasonId").asText());
        added(UUID.randomUUID().toString(), "Foreign reason");
        exchange(disable(ids.get(0), tenant, ADMIN), 200);
        List<String> listed = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            var response = CatalogHttpTest.json(exchange(as(get(BASE).param("page", "" + page).param("limit", "2"),
                    tenant, ADMIN), 200));
            assertThat(response.at("/meta/page").intValue()).isEqualTo(page);
            assertThat(response.at("/meta/limit").intValue()).isEqualTo(2);
            assertThat(response.at("/meta/total").longValue()).isEqualTo(5);
            assertThat(response.at("/meta/totalPages").longValue()).isEqualTo(3);
            response.get("data").forEach(item -> listed.add(item.get("reasonId").asText()));
        }
        assertThat(listed).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(CatalogHttpTest.json(exchange(as(get(BASE).param("limit", "100"), tenant, ADMIN), 200))
                .findValuesAsText("reasonStatus")).containsExactlyInAnyOrder("DISABLED", "ENABLED", "ENABLED",
                "ENABLED", "ENABLED");
        var defaults = CatalogHttpTest.json(exchange(as(get(BASE), tenant, ADMIN), 200));
        assertThat(defaults.at("/meta/limit").intValue()).isEqualTo(20);
        assertThat(defaults.get("data").size()).isEqualTo(5);
    }

    @ParameterizedTest @ValueSource(strings = {"page=0", "limit=0", "limit=101", "page=x", "other=1"})
    void anInvalidPaginationIsRefusedNamingTheParameter(String query) throws Exception {
        String[] pair = query.split("=");
        CatalogHttpTest.assertField(exchange(as(get(BASE).param(pair[0], pair[1]), UUID.randomUUID().toString(), ADMIN), 400),
                pair[0]);
    }

    @Test void disablingKeepsTheReasonReadableAndRepeatingItAnswers200WithTheSameState() throws Exception {
        String tenant = UUID.randomUUID().toString();
        String id = added(tenant, "First").get("reasonId").asText();
        added(tenant, "Second");
        var disabled = CatalogHttpTest.json(exchange(disable(id, tenant, ADMIN), 200));
        assertThat(disabled.get("reasonId").asText()).isEqualTo(id);
        assertThat(disabled.get("reasonStatus").asText()).isEqualTo("DISABLED");
        assertThat(CatalogHttpTest.json(exchange(disable(id, tenant, ADMIN), 200))).isEqualTo(disabled);
        assertThat(rows(tenant)).isEqualTo(2);
    }

    @Test void theLastEnabledReasonCannotBeDisabled() throws Exception {
        String tenant = UUID.randomUUID().toString();
        String first = added(tenant, "First").get("reasonId").asText();
        String second = added(tenant, "Second").get("reasonId").asText();
        exchange(disable(first, tenant, ADMIN), 200);
        assertBusinessRule(exchange(disable(second, tenant, ADMIN), 422));
        assertThat(jdbc.queryForObject("SELECT status FROM commercial_reservation.authorizable_reason "
                + "WHERE authorizable_reason_id = ?", String.class, UUID.fromString(second))).isEqualTo("ENABLED");
    }

    @Test void anUnknownOrForeignReasonAnswersNotFoundAndAMalformedIdentifierIsRefused() throws Exception {
        String tenant = UUID.randomUUID().toString();
        String foreign = added(UUID.randomUUID().toString(), "Foreign").get("reasonId").asText();
        var missing = CatalogHttpTest.json(exchange(disable(UUID.randomUUID().toString(), tenant, ADMIN), 404));
        assertThat(missing.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(CatalogHttpTest.json(exchange(disable(foreign, tenant, ADMIN), 404))).isEqualTo(missing);
        CatalogHttpTest.assertField(exchange(disable("not-a-uuid", tenant, ADMIN), 400), "reasonId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATIONAL_COLLABORATOR", "MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER"})
    void onlyTheTenantAdministratorUsesTheRoutes(String role) throws Exception {
        String tenant = UUID.randomUUID().toString();
        String id = added(tenant, "Only admin").get("reasonId").asText();
        added(tenant, "Another");
        // An invalid body must not leak validation details to a caller without permission.
        CatalogHttpTest.assertForbidden(exchange(add(Map.of(), "reason-key-7", tenant, role), 403));
        CatalogHttpTest.assertForbidden(exchange(as(get(BASE), tenant, role), 403));
        CatalogHttpTest.assertForbidden(exchange(disable(id, tenant, role), 403));
        assertThat(rows(tenant)).isEqualTo(2);
    }

    @Test void aRequestWithoutTokenIsUnauthorized() throws Exception {
        CatalogHttpTest.assertError(exchange(get(BASE), 401), "UNAUTHORIZED", "authentication is required");
        exchange(post(BASE + "/" + UUID.randomUUID() + "/disable"), 401);
    }

    @Test void twoConcurrentDisablesCannotLeaveTheTenantWithoutAnEnabledReason() throws Exception {
        for (int round = 0; round < 5; round++) {
            String tenant = UUID.randomUUID().toString();
            String first = added(tenant, "First").get("reasonId").asText();
            String second = added(tenant, "Second").get("reasonId").asText();
            var start = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                List<Callable<Integer>> calls = List.of(() -> disableAfter(start, first, tenant),
                        () -> disableAfter(start, second, tenant));
                var futures = calls.stream().map(pool::submit).toList();
                start.countDown();
                List<Integer> codes = new ArrayList<>();
                for (var future : futures) codes.add(future.get());
                assertThat(codes).containsExactlyInAnyOrder(200, 422);
            } finally {
                pool.shutdownNow();
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.authorizable_reason "
                    + "WHERE tenant_id = ? AND status = 'ENABLED'", Long.class, UUID.fromString(tenant))).isEqualTo(1L);
        }
    }

    private int disableAfter(CountDownLatch start, String id, String tenant) throws Exception {
        start.await();
        return mvc.perform(disable(id, tenant, ADMIN).header("X-Correlation-Id", TRACE)).andReturn()
                .getResponse().getStatus();
    }
}
