package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static co.edu.corhuila.multi_tour.commercial_reservation.app.EstablishmentHttpTest.BASE;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.EstablishmentHttpTest.PNG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * T-08: one test per box of Annex C "Como se verifica (5.3.12)" for the establishment resource.
 * Names are boxNN_group_..., numbered in the order of the list. Boxes with no counterpart in this
 * HU (money, JSON body, confirm/cancel states, createdBefore, dates) are not invented: they are
 * listed as not applicable in the T-08 record.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
class EstablishmentAnnexCChecklistTest {
    static final String ADMIN = "TENANT_ADMINISTRATOR";
    @Autowired MockMvc mvc;
    String tenant;

    @BeforeEach void tenant() { tenant = UUID.randomUUID().toString(); }

    // Authentication
    @Test void box01_auth_healthWithoutTokenAnswers200() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test void box02_auth_noTokenAnswers401() throws Exception {
        assertUnauthorized(mvc.perform(get(BASE).header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn());
    }

    @ParameterizedTest @ValueSource(strings = {"none", "HS256", "RS512"})
    void box03_auth_algorithmOtherThanRs256Answers401(String algorithm) throws Exception {
        bearer(TestJwtKeys.token(Map.of("alg", algorithm), TestJwtKeys.claims(), TestJwtKeys.TRUSTED));
    }

    @Test void box04_auth_expiredTokenAnswers401() throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("exp", TestJwtKeys.NOW.minusSeconds(31).getEpochSecond());
        bearer(TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED));
    }

    @Test void box05_auth_tokenSignedWithAnotherKeyAnswers401() throws Exception {
        bearer(TestJwtKeys.token(Map.of("alg", "RS256"), TestJwtKeys.claims(), TestJwtKeys.OTHER));
    }

    // Envelope and correlation
    @Test void box06_envelope_malformedIdAnswers400NamingTheField() throws Exception {
        var body = CatalogHttpTest.json(exchange(get(BASE + "/not-a-uuid"), 400));
        assertThat(body.get("error").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("associationId");
    }

    @Test void box07_envelope_errorCarriesErrorMessageAndTraceId() throws Exception {
        var body = CatalogHttpTest.json(exchange(get(BASE + "/" + UUID.randomUUID()), 404));
        assertThat(body.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.get("traceId").asText()).isNotBlank();
    }

    @Test void box08_envelope_traceIdEqualsTheReceivedCorrelationId() throws Exception {
        var body = CatalogHttpTest.json(exchange(get(BASE + "/" + UUID.randomUUID()), 404));
        assertThat(body.get("traceId").asText()).isEqualTo(CatalogHttpTest.TRACE);
    }

    @Test void box09_envelope_responseReturnsTheSameCorrelationId() throws Exception {
        assertThat(exchange(get(BASE), 200).getResponse().getHeader("X-Correlation-Id")).isEqualTo(CatalogHttpTest.TRACE);
    }

    @Test void box10_envelope_withoutItACorrelationIdIsGenerated() throws Exception {
        var result = mvc.perform(get(BASE).header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, ADMIN)))
                .andExpect(status().isOk()).andReturn();
        assertThat(UUID.fromString(result.getResponse().getHeader("X-Correlation-Id"))).isNotNull();
    }

    @Test void box11_envelope_missingResourceAnswers404() throws Exception {
        CatalogHttpTest.assertError(exchange(get(BASE + "/" + UUID.randomUUID()), 404), "NOT_FOUND", "resource not found");
    }

    @Test void box12_envelope_unknownRouteAnswers404WithTheEnvelope() throws Exception {
        CatalogHttpTest.assertError(exchange(get(BASE + "-missing"), 404), "NOT_FOUND", "resource not found");
    }

    // Validation
    @Test void box13_validation_invalidBodyAnswers400() throws Exception {
        var request = multipart(BASE).param("establishmentType", "HOSTEL").header("Idempotency-Key", "annex-c-key");
        assertThat(CatalogHttpTest.json(exchange(request, 400)).get("error").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @ParameterizedTest @ValueSource(strings = {"establishmentType", "commercialName", "commercialInformation", "image", "Idempotency-Key"})
    void box14_validation_detailsNameTheMissingField(String missing) throws Exception {
        var request = multipart(BASE);
        if (!missing.equals("image")) request.file(new MockMultipartFile("image", "image.png", "image/png", PNG));
        if (!missing.equals("establishmentType")) request.param("establishmentType", "HOTEL");
        if (!missing.equals("commercialName")) request.param("commercialName", "Hotel");
        if (!missing.equals("commercialInformation")) request.param("commercialInformation", "Contact");
        if (!missing.equals("Idempotency-Key")) request.header("Idempotency-Key", "annex-c-key");
        var details = CatalogHttpTest.json(exchange(request, 400)).get("details");
        assertThat(details.get(0).get("field").asText()).isEqualTo(missing);
    }

    // Idempotency
    @Test void box15_idempotency_firstSendAnswers201WithAUuid() throws Exception {
        var id = CatalogHttpTest.json(exchange(upload(), 201)).get("associationId").asText();
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    }

    @Test void box16_idempotency_creationAnswersLocation() throws Exception {
        var result = exchange(upload(), 201);
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo(BASE + "/" + CatalogHttpTest.json(result).get("associationId").asText());
    }

    @Test void box17_idempotency_retryWithTheSameKeyAnswers200WithTheSameId() throws Exception {
        var first = CatalogHttpTest.json(exchange(upload(), 201));
        assertThat(CatalogHttpTest.json(exchange(upload(), 200)).get("associationId"))
                .isEqualTo(first.get("associationId"));
    }

    // Representation: the resource has no date or money field, so only camelCase and types apply
    @Test void box18_representation_camelCaseFieldsWithTheContractTypes() throws Exception {
        var body = CatalogHttpTest.json(exchange(upload(), 201));
        List<String> names = new ArrayList<>();
        body.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("associationId", "establishmentType", "commercialName",
                "commercialInformation", "imageReference", "associationStatus");
        names.forEach(name -> assertThat(body.get(name).isTextual()).as(name).isTrue());
    }

    // Transitions: inactivate/reactivate are the analogue of cancel/confirm, and both are idempotent
    @Test void box19_transitions_inactivateAnActiveAssociationAnswers200() throws Exception {
        var item = CatalogHttpTest.json(exchange(upload(), 201));
        var inactive = CatalogHttpTest.json(exchange(post(path(item) + "/inactivate"), 200));
        assertThat(inactive.get("associationStatus").asText()).isEqualTo("INACTIVE");
    }

    @Test void box20_transitions_inactivateTwiceAnswers200() throws Exception {
        var item = CatalogHttpTest.json(exchange(upload(), 201));
        var first = CatalogHttpTest.json(exchange(post(path(item) + "/inactivate"), 200));
        assertThat(CatalogHttpTest.json(exchange(post(path(item) + "/inactivate"), 200))).isEqualTo(first);
    }

    @Test void box21_transitions_reactivateAnInactiveAssociationAnswers200() throws Exception {
        var item = CatalogHttpTest.json(exchange(upload(), 201));
        exchange(post(path(item) + "/inactivate"), 200);
        assertThat(CatalogHttpTest.json(exchange(post(path(item) + "/reactivate"), 200))).isEqualTo(item);
    }

    @Test void box22_transitions_deleteRouteDoesNotExist() throws Exception {
        var item = CatalogHttpTest.json(exchange(upload(), 201));
        exchange(delete(path(item)), 404);
        exchange(get(path(item)), 200);
    }

    // Listing
    @Test void box23_listing_dataAndMetaWithNoMoreThanLimit() throws Exception {
        seed(5);
        var body = CatalogHttpTest.json(exchange(get(BASE).param("limit", "2"), 200));
        assertThat(body.get("data").size()).isEqualTo(2);
        assertThat(body.get("meta").get("total").intValue()).isEqualTo(5);
    }

    @Test void box24_listing_metaRepeatsPageAndLimit() throws Exception {
        seed(4);
        var meta = CatalogHttpTest.json(exchange(get(BASE).param("page", "2").param("limit", "3"), 200)).get("meta");
        assertThat(meta.get("page").intValue()).isEqualTo(2);
        assertThat(meta.get("limit").intValue()).isEqualTo(3);
    }

    @Test void box25_listing_withoutLimitTheDefaultIs20() throws Exception {
        seed(21);
        var body = CatalogHttpTest.json(exchange(get(BASE), 200));
        assertThat(body.get("meta").get("limit").intValue()).isEqualTo(20);
        assertThat(body.get("data").size()).isEqualTo(20);
    }

    @Test void box26_listing_newestFirstWithADeterministicTieBreak() throws Exception {
        var ids = seed(4);
        ids.sort(Comparator.reverseOrder());
        var data = CatalogHttpTest.json(exchange(get(BASE), 200)).get("data");
        for (int i = 0; i < ids.size(); i++) {
            assertThat(data.get(i).get("associationId").asText()).isEqualTo(ids.get(i));
        }
    }

    @ParameterizedTest @CsvSource({"limit,101", "limit,0", "page,0"})
    void box27_listing_limitOver100Answers400(String field, String value) throws Exception {
        var body = CatalogHttpTest.json(exchange(get(BASE).param(field, value), 400));
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo(field);
    }

    @ParameterizedTest @CsvSource({"associationStatus,OTHER", "establishmentType,OTHER"})
    void box28_listing_unknownFilterValueAnswers400(String field, String value) throws Exception {
        var body = CatalogHttpTest.json(exchange(get(BASE).param(field, value), 400));
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo(field);
    }

    private List<String> seed(int count) throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(CatalogHttpTest.json(exchange(upload().header("Idempotency-Key", "annex-c-seed-" + i), 201))
                    .get("associationId").asText());
        }
        return ids;
    }

    private MockMultipartHttpServletRequestBuilder upload() {
        var request = multipart(BASE).file(new MockMultipartFile("image", "image.png", "image/png", PNG));
        request.param("establishmentType", "HOTEL").param("commercialName", "Hotel annex C")
                .param("commercialInformation", "Contact").header("Idempotency-Key", "annex-c-key");
        return request;
    }

    private String path(JsonNode item) { return BASE + "/" + item.get("associationId").asText(); }

    private MvcResult exchange(MockHttpServletRequestBuilder request, int code) throws Exception {
        var result = mvc.perform(request.header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, ADMIN))
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    private void bearer(String token) throws Exception {
        assertUnauthorized(mvc.perform(get(BASE).header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn());
    }

    private void assertUnauthorized(MvcResult result) throws Exception {
        status().isUnauthorized().match(result);
        CatalogHttpTest.assertError(result, "UNAUTHORIZED", "authentication is required");
    }
}
