package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogHttpTest {
    static final String BASE = "/api/v1/catalog/services";
    static final String TRACE = "catalog-http-test";
    static final List<String> FIELDS = List.of("serviceId", "serviceType", "commercialName", "catalogStatus",
            "baseRateCents", "baseOperationalCostCents", "configuredCapacity", "transportRequired",
            "highRisk", "createdAt", "updatedAt");
    final CatalogContract contract = new CatalogContract();
    @Autowired MockMvc mvc;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;
    @Autowired CatalogHttpFixture.MemoryKeys keys;

    @BeforeEach void clearStores() { catalog.items.clear(); keys.keys.clear(); }

    static Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceType", "TOUR"); body.put("commercialName", "Test tour");
        body.put("baseRateCents", 35000000L); body.put("baseOperationalCostCents", 21000000L);
        body.put("configuredCapacity", 18); body.put("transportRequired", true); body.put("highRisk", false);
        return body;
    }

    static String token(String tenant, String role) throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("tenantId", tenant); claims.put("roles", List.of(role));
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    MvcResult exchange(MockHttpServletRequestBuilder request, int code, boolean invalidRequest) throws Exception {
        MvcResult result = mvc.perform(request.header("X-Correlation-Id", TRACE)).andReturn();
        contract.validate(result, invalidRequest);
        status().is(code).match(result);
        return result;
    }

    MockHttpServletRequestBuilder create(Map<String, Object> body, String key, String tenant, String role)
            throws Exception {
        var request = post(BASE).header("Authorization", "Bearer " + token(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body));
        if (key != null) request.header("Idempotency-Key", key);
        return request;
    }

    MockHttpServletRequestBuilder create(Map<String, Object> body) throws Exception {
        return create(body, "catalog-key", TestJwtKeys.TENANT, "TENANT_ADMINISTRATOR");
    }

    MockHttpServletRequestBuilder read(String id, String tenant, String role) throws Exception {
        return get(BASE + "/" + id).header("Authorization", "Bearer " + token(tenant, role));
    }

    static JsonNode json(MvcResult result) throws Exception {
        return TestJwtKeys.JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    static void assertDto(JsonNode item) {
        List<String> actual = new ArrayList<>(); item.fieldNames().forEachRemaining(actual::add);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(FIELDS);
        assertThat(item.get("baseRateCents").isIntegralNumber()).isTrue();
        assertThat(item.get("baseOperationalCostCents").isIntegralNumber()).isTrue();
        assertThat(item.get("createdAt").asText()).endsWith("Z");
        assertThat(item.get("updatedAt").asText()).endsWith("Z");
        Instant.parse(item.get("createdAt").asText()); Instant.parse(item.get("updatedAt").asText());
    }

    static void assertField(MvcResult result, String field) throws Exception {
        JsonNode error = json(result);
        assertThat(error.get("error").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.at("/details/0/field").asText()).isEqualTo(field);
        assertThat(error.get("traceId").asText()).isEqualTo(TRACE);
    }

    CatalogService seed() {
        CatalogService item = CatalogService.register(UUID.randomUUID(), UUID.fromString(TestJwtKeys.TENANT),
                CatalogServiceType.TOUR, "Stored tour", CommercialTerms.of(100L, 50L, 18, true, false),
                TestJwtKeys.NOW);
        catalog.save(item); return item;
    }

    @Test void createAnswers201WithLocationAndTheItem() throws Exception {
        var result = exchange(create(body()), 201, false);
        var item = json(result); assertDto(item);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(BASE + "/" + item.get("serviceId").asText());
        assertThat(item.get("catalogStatus").asText()).isEqualTo("ACTIVE");
        assertThat(item.get("commercialName").asText()).isEqualTo("Test tour");
        assertThat(item.get("baseRateCents").longValue()).isEqualTo(35000000L);
        assertThat(item.get("baseOperationalCostCents").longValue()).isEqualTo(21000000L);
    }

    @ParameterizedTest @ValueSource(strings = {"baseRateCents", "baseOperationalCostCents", "configuredCapacity"})
    void missingFieldIsNamedInDetails(String field) throws Exception {
        var body = body(); body.remove(field);
        assertField(exchange(create(body), 400, !field.equals("configuredCapacity")), field);
    }

    @Test void sameIdempotencyKeyAnswers200WithTheSameResource() throws Exception {
        var original = json(exchange(create(body()), 201, false));
        var replay = json(exchange(create(body()), 200, false));
        assertThat(replay).isEqualTo(original);
        var other = json(exchange(create(body(), "catalog-key", UUID.randomUUID().toString(),
                "TENANT_ADMINISTRATOR"), 201, false));
        assertThat(other.get("serviceId")).isNotEqualTo(original.get("serviceId"));
    }

    @ParameterizedTest @ValueSource(ints = {0, 7, 129})
    void invalidIdempotencyKeyNamesTheHeader(int length) throws Exception {
        String key = length == 0 ? null : "k".repeat(length);
        var result = exchange(create(body(), key, TestJwtKeys.TENANT, "TENANT_ADMINISTRATOR"), 400, true);
        assertField(result, "Idempotency-Key");
        assertThat(json(result).at("/details/0/message").asText()).isEqualTo("header required, 8 to 128 characters");
    }

    @ParameterizedTest @ValueSource(ints = {8, 128})
    void boundaryIdempotencyKeysAreAccepted(int length) throws Exception {
        exchange(create(body(), "k".repeat(length), TestJwtKeys.TENANT, "TENANT_ADMINISTRATOR"), 201, false);
    }

    @ParameterizedTest @CsvSource({"tenantId,tenant", "unknown,extra", "baseRateCents,10.5",
            "baseOperationalCostCents,10.5", "baseRateCents,100", "transportRequired,true"})
    void strictJsonNamesTheRejectedField(String field, String value) throws Exception {
        var body = body();
        body.put(field, value.equals("10.5") ? Double.valueOf(value) : value);
        assertField(exchange(create(body), 400, true), field);
    }

    @ParameterizedTest @ValueSource(strings = {"serviceType", "commercialName", "transportRequired", "highRisk"})
    void remainingRequiredFieldsCannotSilentlyDefault(String field) throws Exception {
        var body = body(); body.remove(field);
        assertField(exchange(create(body), 400, true), field);
    }

    @ParameterizedTest @ValueSource(strings = {"serviceType", "transportRequired", "highRisk", "configuredCapacity"})
    void explicitNullDoesNotCoerceOrDefault(String field) throws Exception {
        var body = body(); body.put(field, null);
        assertField(exchange(create(body), 400, true), field);
    }

    @Test void responsesAreDtosNeverTheDomainEntity() throws Exception {
        var item = json(exchange(create(body()), 201, false)); assertDto(item);
        var fetched = json(exchange(read(item.get("serviceId").asText(), TestJwtKeys.TENANT,
                "TENANT_ADMINISTRATOR"), 200, false));
        assertDto(fetched); assertThat(fetched).isEqualTo(item);
    }

    @Test void transportationMayOmitCapacityAndReturnsItsNull() throws Exception {
        var body = body(); body.put("serviceType", "TRANSPORTATION"); body.remove("configuredCapacity");
        var item = json(exchange(create(body), 201, false)); assertDto(item);
        assertThat(item.get("configuredCapacity").isNull()).isTrue();
        assertThat(item.get("transportRequired").booleanValue()).isFalse();
    }

    @Test void getResolvesAnInactiveItem() throws Exception {
        var item = seed(); item.inactivate(UUID.randomUUID(), TestJwtKeys.NOW);
        var fetched = json(exchange(read(item.serviceId().toString(), TestJwtKeys.TENANT,
                "TENANT_ADMINISTRATOR"), 200, false));
        assertThat(fetched.get("catalogStatus").asText()).isEqualTo("INACTIVE");
    }

    @Test void malformedUuidNamesServiceId() throws Exception {
        assertField(exchange(read("not-a-uuid", TestJwtKeys.TENANT, "TENANT_ADMINISTRATOR"), 400, true), "serviceId");
    }

    @Test void unsupportedContentTypeNamesTheHeader() throws Exception {
        assertField(exchange(create(body()).contentType(MediaType.TEXT_PLAIN), 400, true), "Content-Type");
    }

    @Test void absentBodyNamesBody() throws Exception {
        var request = post(BASE).header("Authorization", "Bearer " + TestJwtKeys.token())
                .header("Idempotency-Key", "catalog-key").contentType(MediaType.APPLICATION_JSON);
        assertField(exchange(request, 400, true), "body");
    }

    @Test void otherTenantItemAnswersAsNonexistent() throws Exception {
        var item = seed();
        exchange(read(item.serviceId().toString(), TestJwtKeys.TENANT, "TENANT_ADMINISTRATOR"), 200, false);
        String stranger = UUID.randomUUID().toString();
        var other = json(exchange(read(item.serviceId().toString(), stranger, "TENANT_ADMINISTRATOR"), 404, false));
        var missing = json(exchange(read(UUID.randomUUID().toString(), stranger, "TENANT_ADMINISTRATOR"), 404, false));
        assertThat(other).isEqualTo(missing);
        assertThat(other.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(other.get("traceId").asText()).isEqualTo(TRACE);
    }

    @Test void collaboratorCannotCreate() throws Exception {
        assertForbidden(exchange(create(body(), "catalog-key", TestJwtKeys.TENANT,
                "OPERATIONAL_COLLABORATOR"), 403, false));
    }

    @ParameterizedTest @ValueSource(strings = {"TENANT_ADMINISTRATOR", "OPERATIONAL_COLLABORATOR"})
    void permittedRolesCanRead(String role) throws Exception {
        exchange(read(seed().serviceId().toString(), TestJwtKeys.TENANT, role), 200, false);
    }

    @ParameterizedTest @CsvSource({"POST,OTHER,false", "GET,OTHER,false",
            "POST,TENANT_ADMINISTRATOR,true", "GET,TENANT_ADMINISTRATOR,true",
            "POST,OPERATIONAL_COLLABORATOR,true", "GET,OPERATIONAL_COLLABORATOR,true"})
    void unrelatedRolesAndMissingTenantsAreForbidden(String method, String role, boolean noTenant) throws Exception {
        String tenant = noTenant ? null : TestJwtKeys.TENANT;
        var request = method.equals("POST") ? create(body(), "catalog-key", tenant, role)
                : read(seed().serviceId().toString(), tenant, role);
        assertForbidden(exchange(request, 403, false));
    }

    static void assertForbidden(MvcResult result) throws Exception {
        var error = json(result);
        assertThat(error.get("error").asText()).isEqualTo("FORBIDDEN");
        assertThat(error.get("message").asText()).isEqualTo("access is forbidden");
        assertThat(error.get("traceId").asText()).isEqualTo(TRACE);
        List<String> fields = new ArrayList<>(); error.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("error", "message", "traceId");
    }

    @Test @Order(Integer.MAX_VALUE)
    void contractOperationsAreExercised() throws Exception {
        exchange(get("/health"), 200, false);
        contract.assertCoverage();
    }
}
