package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    // AC-03.14: no route deletes a rule (the PATCH exists since the decision of 2026-11-03).
    @Test void noRouteDeletesARule() throws Exception {
        var rule = seed();
        CatalogHttpTest.assertError(exchange(delete(BASE + "/" + rule).header("Authorization", "Bearer "
                + CatalogHttpTest.token(TestJwtKeys.TENANT, ADMIN)), 404), "NOT_FOUND", "resource not found");
        assertThat(rules.rules).hasSize(1);
    }

    // DP-04: the flag is part of the request and the response; absent means true.
    @Test void stackableIsEchoedAndDefaultsToTrue() throws Exception {
        assertThat(CatalogHttpTest.json(exchange(create(body(), "rule-key-s1", TestJwtKeys.TENANT, ADMIN), 201))
                .get("stackable").asBoolean()).isTrue();
        var request = body(); request.put("stackable", false);
        var stops = CatalogHttpTest.json(exchange(create(request, "rule-key-s2", TestJwtKeys.TENANT, ADMIN), 201));
        assertThat(stops.get("stackable").asBoolean()).isFalse();
        assertThat(CatalogHttpTest.json(exchange(read(stops.get("discountRuleId").asText(), TestJwtKeys.TENANT, ADMIN), 200))
                .get("stackable").asBoolean()).isFalse();
    }

    @Test void aNonBooleanStackableIsRefusedNamingTheField() throws Exception {
        var request = body(); request.put("stackable", "maybe");
        CatalogHttpTest.assertField(exchange(create(request, "rule-key-s3", TestJwtKeys.TENANT, ADMIN), 400), "stackable");
        assertThat(rules.rules).isEmpty();
    }

    // AC-03.16
    @Test void patchChangesOnlyTheFieldsSentAndWritesOneEvent() throws Exception {
        var rule = seed();
        var updated = CatalogHttpTest.json(exchange(patch(rule, "{\"name\":\"Late booking\",\"stackable\":false}"), 200));
        assertThat(updated.get("name").asText()).isEqualTo("Late booking");
        assertThat(updated.get("stackable").asBoolean()).isFalse();
        assertThat(updated.get("validFrom").asText()).isEqualTo("2026-11-01");
        assertThat(updated.get("priority").asInt()).isEqualTo(1);
        assertThat(updated.get("percentageBasisPoints").asInt()).isEqualTo(1500);
        assertThat(updated.get("ruleStatus").asText()).isEqualTo("ACTIVE");
        assertThat(updated.has("tenantId")).isFalse();
        assertThat(rules.events).hasSize(2);
        assertThat(rules.events.get(1).previous().name()).isEqualTo("Early booking");
        assertThat(rules.events.get(1).current().name()).isEqualTo("Late booking");
    }

    @Test void patchCanReplaceThePercentageByAnAmountAndAddOrRemoveTheCap() throws Exception {
        var rule = seed();
        var amount = CatalogHttpTest.json(exchange(patch(rule, "{\"amountCents\":250000,\"capCents\":900000}"), 200));
        assertThat(amount.get("amountCents").asLong()).isEqualTo(250000L);
        assertThat(amount.has("percentageBasisPoints")).isFalse();
        assertThat(amount.get("capCents").asLong()).isEqualTo(900000L);
        var noCap = CatalogHttpTest.json(exchange(patch(rule, "{\"capCents\":null}"), 200));
        assertThat(noCap.has("capCents")).isFalse();
        assertThat(noCap.get("amountCents").asLong()).isEqualTo(250000L);
    }

    @Test void patchCanChangeTheCoverage() throws Exception {
        var rule = seed();
        String service = UUID.randomUUID().toString();
        var selected = CatalogHttpTest.json(exchange(patch(rule,
                "{\"serviceCoverage\":\"SELECTED\",\"serviceIds\":[\"" + service + "\"]}"), 200));
        assertThat(selected.get("serviceCoverage").asText()).isEqualTo("SELECTED");
        assertThat(selected.get("serviceIds").get(0).asText()).isEqualTo(service);
        var all = CatalogHttpTest.json(exchange(patch(rule, "{\"serviceCoverage\":\"ALL\"}"), 200));
        assertThat(all.get("serviceCoverage").asText()).isEqualTo("ALL");
        assertThat(all.get("serviceIds")).isEmpty();
    }

    @Test void aBodyThatChangesNothingAnswersOkAndEmitsNothing() throws Exception {
        var rule = seed();
        exchange(patch(rule, "{\"name\":\"Early booking\",\"priority\":1,\"stackable\":true}"), 200);
        assertThat(rules.events).hasSize(1);
    }

    @Test void patchWorksOnAnInactiveRuleAndKeepsItInactive() throws Exception {
        var rule = seed();
        exchange(inactivate(rule, TestJwtKeys.TENANT, ADMIN), 200);
        var updated = CatalogHttpTest.json(exchange(patch(rule, "{\"priority\":4}"), 200));
        assertThat(updated.get("priority").asInt()).isEqualTo(4);
        assertThat(updated.get("ruleStatus").asText()).isEqualTo("INACTIVE");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"validFrom|{\"validFrom\":\"2026-12-01\"}", "validTo|{\"validTo\":\"01/12/2026\"}",
            "name|{\"name\":\" \"}", "name|{\"name\":null}", "priority|{\"priority\":0}", "priority|{\"priority\":\"high\"}",
            "percentageBasisPoints|{\"percentageBasisPoints\":10001}", "amountCents|{\"amountCents\":100,\"percentageBasisPoints\":100}",
            "capCents|{\"capCents\":0}", "calculationBase|{\"calculationBase\":\"SOMETHING\"}", "stackable|{\"stackable\":null}",
            "serviceCoverage|{\"serviceCoverage\":\"SELECTED\",\"serviceIds\":[]}", "serviceIds|{\"serviceIds\":[]}",
            "ruleStatus|{\"ruleStatus\":\"INACTIVE\"}", "tenantId|{\"tenantId\":\"x\"}", "discountRuleId|{\"discountRuleId\":\"x\"}",
            "createdAt|{\"createdAt\":\"2026-01-01T00:00:00Z\"}", "body|{}"})
    void anInvalidPatchIsRefusedNamingTheFieldAndChangesNothing(String field, String json) throws Exception {
        var rule = seed();
        CatalogHttpTest.assertField(exchange(patch(rule, json), 400), field);
        assertThat(rules.events).hasSize(1);
        assertThat(rules.rules.values().iterator().next().name()).isEqualTo("Early booking");
    }

    @Test void aRuleOfAnotherTenantAndAnUnknownOneAnswerNotFoundOnPatchAndReactivate() throws Exception {
        var rule = seed();
        String other = "00000000-0000-0000-0000-000000000999";
        CatalogHttpTest.assertError(exchange(patch(rule, "{\"name\":\"x\"}", other, ADMIN), 404), "NOT_FOUND", "resource not found");
        CatalogHttpTest.assertError(exchange(patch(UUID.randomUUID().toString(), "{\"name\":\"x\"}"), 404), "NOT_FOUND", "resource not found");
        CatalogHttpTest.assertError(exchange(reactivate(rule, other, ADMIN), 404), "NOT_FOUND", "resource not found");
        assertThat(rules.rules.values().iterator().next().name()).isEqualTo("Early booking");
        assertThat(rules.events).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATIONAL_COLLABORATOR", "MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER"})
    void patchAndReactivateAreForbiddenToTheOtherFiveRoles(String role) throws Exception {
        var rule = seed();
        exchange(inactivate(rule, TestJwtKeys.TENANT, ADMIN), 200);
        CatalogHttpTest.assertForbidden(exchange(patch(rule, "{}", TestJwtKeys.TENANT, role), 403));
        CatalogHttpTest.assertForbidden(exchange(patch(rule, "{\"name\":\"x\"}", TestJwtKeys.TENANT, role), 403));
        CatalogHttpTest.assertForbidden(exchange(reactivate(rule, TestJwtKeys.TENANT, role), 403));
        assertThat(rules.events).hasSize(2);
        assertThat(rules.rules.values().iterator().next().isActive()).isFalse();
    }

    @Test void patchAndReactivateWithoutTokenAreUnauthorized() throws Exception {
        CatalogHttpTest.assertError(exchange(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch(BASE + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{}"), 401),
                "UNAUTHORIZED", "authentication is required");
        CatalogHttpTest.assertError(exchange(post(BASE + "/" + UUID.randomUUID() + "/reactivate"), 401),
                "UNAUTHORIZED", "authentication is required");
    }

    // AC-03.17
    @Test void reactivatingBringsTheRuleBackAndRepeatingItEmitsNothing() throws Exception {
        var rule = seed();
        exchange(inactivate(rule, TestJwtKeys.TENANT, ADMIN), 200);
        var back = CatalogHttpTest.json(exchange(reactivate(rule, TestJwtKeys.TENANT, ADMIN), 200));
        assertThat(back.get("ruleStatus").asText()).isEqualTo("ACTIVE");
        assertThat(back.get("name").asText()).isEqualTo("Early booking");
        assertThat(rules.events).hasSize(3);
        assertThat(rules.events.get(2).previous().status().name()).isEqualTo("INACTIVE");
        var again = CatalogHttpTest.json(exchange(reactivate(rule, TestJwtKeys.TENANT, ADMIN), 200));
        assertThat(again).isEqualTo(back);
        assertThat(rules.events).hasSize(3);
    }

    @Test void aNonUuidIdentifierIsAValidationErrorOnPatchAndReactivate() throws Exception {
        CatalogHttpTest.assertField(exchange(patch("not-a-uuid", "{\"name\":\"x\"}"), 400), "discountRuleId");
        CatalogHttpTest.assertField(exchange(reactivate("not-a-uuid", TestJwtKeys.TENANT, ADMIN), 400), "discountRuleId");
    }

    private MockHttpServletRequestBuilder patch(String id, String json) throws Exception {
        return patch(id, json, TestJwtKeys.TENANT, ADMIN);
    }

    private MockHttpServletRequestBuilder patch(String id, String json, String tenant, String role) throws Exception {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE + "/" + id)
                .header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockHttpServletRequestBuilder reactivate(String id, String tenant, String role) throws Exception {
        return post(BASE + "/" + id + "/reactivate").header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, role));
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
