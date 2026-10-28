package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-003 over HTTP: AC-03.1 create, AC-03.3/03.4 validation, AC-03.13 tenant isolation, AC-03.14 inactivation.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, DiscountRuleHttpFixture.class})
class DiscountRuleHttpTest {
    static final String BASE = "/api/v1/commercial-reservation/discount-rules";
    static final String TRACE = CatalogHttpTest.TRACE;
    static final String ADMIN = "TENANT_ADMINISTRATOR";
    @Autowired MockMvc mvc;
    @Autowired DiscountRuleHttpFixture.MemoryDiscountRules rules;
    @Autowired CatalogHttpFixture.MemoryKeys keys;

    @BeforeEach void clear() { rules.rules.clear(); rules.events.clear(); keys.keys.clear(); }

    static Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Early booking"); body.put("validFrom", "2026-11-01"); body.put("validTo", "2026-11-30");
        body.put("priority", 1); body.put("calculationBase", "ORIGINAL_VALUE"); body.put("percentageBasisPoints", 1500);
        return body;
    }

    @Test void adminCreatesAnActiveRuleWithItsLocationAndOneEvent() throws Exception {
        var result = exchange(create(body(), "rule-key-1", TestJwtKeys.TENANT, ADMIN), 201);
        var rule = CatalogHttpTest.json(result);
        assertThat(rule.get("ruleStatus").asText()).isEqualTo("ACTIVE");
        assertThat(rule.get("name").asText()).isEqualTo("Early booking");
        assertThat(rule.get("validFrom").asText()).isEqualTo("2026-11-01");
        assertThat(rule.get("validTo").asText()).isEqualTo("2026-11-30");
        assertThat(rule.get("percentageBasisPoints").asInt()).isEqualTo(1500);
        assertThat(rule.has("amountCents")).isFalse();
        assertThat(rule.has("tenantId")).isFalse();
        assertThat(rule.get("serviceCoverage").asText()).isEqualTo("ALL");
        assertThat(rule.get("serviceIds")).isEmpty();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(BASE + "/" + rule.get("discountRuleId").asText());
        assertThat(rules.events).hasSize(1);
        assertThat(rules.events.get(0).previous()).isNull();
    }

    @Test void aFixedAmountRuleForSelectedServicesWithACapIsRoundTripped() throws Exception {
        String service = UUID.randomUUID().toString();
        var request = body(); request.remove("percentageBasisPoints"); request.put("amountCents", 5000000L);
        request.put("serviceCoverage", "SELECTED"); request.put("serviceIds", List.of(service)); request.put("capCents", 8000000L);
        var rule = CatalogHttpTest.json(exchange(create(request, "rule-key-2", TestJwtKeys.TENANT, ADMIN), 201));
        assertThat(rule.get("amountCents").asLong()).isEqualTo(5000000L);
        assertThat(rule.has("percentageBasisPoints")).isFalse();
        assertThat(rule.get("serviceCoverage").asText()).isEqualTo("SELECTED");
        assertThat(rule.get("serviceIds").get(0).asText()).isEqualTo(service);
        assertThat(rule.get("capCents").asLong()).isEqualTo(8000000L);
    }

    @Test void repeatingTheSameKeyReturnsTheSameRuleWithoutAnotherOne() throws Exception {
        var first = CatalogHttpTest.json(exchange(create(body(), "rule-key-3", TestJwtKeys.TENANT, ADMIN), 201));
        var second = CatalogHttpTest.json(exchange(create(body(), "rule-key-3", TestJwtKeys.TENANT, ADMIN), 200));
        assertThat(second).isEqualTo(first);
        assertThat(rules.rules).hasSize(1);
        assertThat(rules.events).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"", "short"})
    void aMissingOrShortKeyIsRefusedNamingTheHeader(String key) throws Exception {
        var request = create(body(), key.isEmpty() ? null : key, TestJwtKeys.TENANT, ADMIN);
        CatalogHttpTest.assertField(exchange(request, 400), "Idempotency-Key");
        assertThat(rules.rules).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"amountCents,both", "percentageBasisPoints,neither", "validFrom,inverted", "validFrom,badDate",
            "tenantId,tenant", "calculationBase,badBase", "name,noName", "serviceCoverage,emptySelection"})
    void invalidBodiesAreRefusedNamingTheFieldAndCreateNothing(String field, String scenario) throws Exception {
        var request = body();
        switch (scenario) {
            case "both" -> request.put("amountCents", 100000L);
            case "neither" -> request.remove("percentageBasisPoints");
            case "inverted" -> request.put("validFrom", "2026-12-01");
            case "badDate" -> request.put("validFrom", "01/11/2026");
            case "tenant" -> request.put("tenantId", UUID.randomUUID().toString());
            case "badBase" -> request.put("calculationBase", "SOMETHING");
            case "noName" -> request.remove("name");
            default -> { request.put("serviceCoverage", "SELECTED"); request.put("serviceIds", List.of()); }
        }
        CatalogHttpTest.assertField(exchange(create(request, "rule-key-4", TestJwtKeys.TENANT, ADMIN), 400), field);
        assertThat(rules.rules).isEmpty();
        assertThat(rules.events).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATIONAL_COLLABORATOR", "MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER"})
    void onlyTheTenantAdministratorUsesTheRoutes(String role) throws Exception {
        var rule = seed();
        // An invalid body must not leak validation details to a caller without permission.
        CatalogHttpTest.assertForbidden(exchange(create(Map.of(), "rule-key-5", TestJwtKeys.TENANT, role), 403));
        CatalogHttpTest.assertForbidden(exchange(read(rule, TestJwtKeys.TENANT, role), 403));
        CatalogHttpTest.assertForbidden(exchange(inactivate(rule, TestJwtKeys.TENANT, role), 403));
        assertThat(rules.rules).hasSize(1);
        assertThat(rules.events).hasSize(1);
        assertThat(rules.rules.values().iterator().next().isActive()).isTrue();
    }

    @Test void aRequestWithoutTokenIsUnauthorized() throws Exception {
        var result = exchange(get(BASE + "/" + UUID.randomUUID()), 401);
        CatalogHttpTest.assertError(result, "UNAUTHORIZED", "authentication is required");
    }

    @Test void readsARuleIncludingAnInactiveOne() throws Exception {
        var rule = seed();
        assertThat(CatalogHttpTest.json(exchange(read(rule, TestJwtKeys.TENANT, ADMIN), 200)).get("ruleStatus").asText())
                .isEqualTo("ACTIVE");
        exchange(inactivate(rule, TestJwtKeys.TENANT, ADMIN), 200);
        assertThat(CatalogHttpTest.json(exchange(read(rule, TestJwtKeys.TENANT, ADMIN), 200)).get("ruleStatus").asText())
                .isEqualTo("INACTIVE");
    }

    @Test void aRuleOfAnotherTenantAndAnUnknownOneAnswerTheSameNotFound() throws Exception {
        var rule = seed();
        String other = "00000000-0000-0000-0000-000000000999";
        CatalogHttpTest.assertError(exchange(read(rule, other, ADMIN), 404), "NOT_FOUND", "resource not found");
        CatalogHttpTest.assertError(exchange(read(UUID.randomUUID().toString(), TestJwtKeys.TENANT, ADMIN), 404),
                "NOT_FOUND", "resource not found");
        CatalogHttpTest.assertError(exchange(inactivate(rule, other, ADMIN), 404), "NOT_FOUND", "resource not found");
        assertThat(rules.rules.values().iterator().next().isActive()).isTrue();
    }

    @Test void aNonUuidIdentifierIsAValidationError() throws Exception {
        CatalogHttpTest.assertField(exchange(read("not-a-uuid", TestJwtKeys.TENANT, ADMIN), 400), "discountRuleId");
    }

    @Test void inactivatingKeepsTheRuleAndRepeatingItEmitsNothing() throws Exception {
        var rule = seed();
        var first = CatalogHttpTest.json(exchange(inactivate(rule, TestJwtKeys.TENANT, ADMIN), 200));
        assertThat(first.get("ruleStatus").asText()).isEqualTo("INACTIVE");
        assertThat(first.get("name").asText()).isEqualTo("Early booking");
        assertThat(rules.events).hasSize(2);
        var again = CatalogHttpTest.json(exchange(inactivate(rule, TestJwtKeys.TENANT, ADMIN), 200));
        assertThat(again).isEqualTo(first);
        assertThat(rules.events).hasSize(2);
    }

    // Contract difference: PATCH has no use case yet (editable fields are not closed), and no route deletes a rule.
    @Test void updateAndDeleteAreNotOfferedYet() throws Exception {
        var rule = seed();
        CatalogHttpTest.assertError(exchange(patch(BASE + "/" + rule).header("Authorization", "Bearer "
                + CatalogHttpTest.token(TestJwtKeys.TENANT, ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\"}"), 404), "NOT_FOUND", "resource not found");
        CatalogHttpTest.assertError(exchange(delete(BASE + "/" + rule).header("Authorization", "Bearer "
                + CatalogHttpTest.token(TestJwtKeys.TENANT, ADMIN)), 404), "NOT_FOUND", "resource not found");
        assertThat(rules.rules).hasSize(1);
    }

    private String seed() throws Exception {
        var rule = CatalogHttpTest.json(exchange(create(body(), "seed-key-1", TestJwtKeys.TENANT, ADMIN), 201));
        keys.keys.clear();
        return rule.get("discountRuleId").asText();
    }

    private MockHttpServletRequestBuilder create(Map<String, Object> request, String key, String tenant, String role)
            throws Exception {
        var builder = post(BASE).header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(request));
        if (key != null) builder.header("Idempotency-Key", key);
        return builder;
    }

    private MockHttpServletRequestBuilder read(String id, String tenant, String role) throws Exception {
        return get(BASE + "/" + id).header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, role));
    }

    private MockHttpServletRequestBuilder inactivate(String id, String tenant, String role) throws Exception {
        return post(BASE + "/" + id + "/inactivate").header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, role));
    }

    private MvcResult exchange(MockHttpServletRequestBuilder request, int code) throws Exception {
        MvcResult result = mvc.perform(request.header("X-Correlation-Id", TRACE)).andReturn();
        status().is(code).match(result);
        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isEqualTo(TRACE);
        return result;
    }
}
