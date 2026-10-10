package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.*;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(HttpFoundationTest.Support.class)
class HttpFoundationTest {
    private static final String PATH = "/test-support/protected";
    private static final String TRACE = "request-123_test.safe";

    @Autowired
    MockMvc mvc;

    @TestConfiguration
    static class Support {
        @Bean @Primary
        Clock fixedClock() {
            return Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC);
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @TestComponent
    @RestController
    static class ProbeController {
        @GetMapping(PATH)
        Map<String, String> protectedResource() {
            return Map.of("status", "ok");
        }

        @GetMapping("/test-support/principal")
        Map<String, Object> principal(HttpServletRequest request) throws Exception {
            Object principal = request.getAttribute("authenticatedPrincipal");
            return Map.of("sub", principal.getClass().getMethod("sub").invoke(principal),
                    "tenantId", String.valueOf(principal.getClass().getMethod("tenantId").invoke(principal)),
                    "roles", principal.getClass().getMethod("roles").invoke(principal));
        }

        @GetMapping("/test-support/ids/{id}")
        String id(@PathVariable("id") UUID id) {
            return id.toString();
        }

        @PostMapping("/test-support/domain")
        void domain() {
            throw new DomainException("commercialName", "commercialName must not be blank");
        }

        @GetMapping("/test-support/missing")
        void missing() {
            throw new CatalogServiceNotFoundException(new UUID(0, 1));
        }

        @GetMapping("/test-support/failure")
        void failure() {
            throw new RuntimeException("boom: secret detail");
        }

        @PostMapping("/test-support/json")
        Map<String, Object> json(@RequestBody Map<String, Object> body) {
            return body;
        }
    }

    private ResultActions withToken(String token) throws Exception {
        return mvc.perform(get(PATH).header("Authorization", "Bearer " + token));
    }

    private ResultActions unauthorized(String token) throws Exception {
        return withToken(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("authentication is required"))
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void protectedRouteWithoutTokenAnswers401() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // Valid RSA signatures isolate the algorithm check from the signature check in mutation tests.
    @Test
    void rejectsATokenWithAlgNoneHs256ExpiredOrWithoutSub_none() throws Exception {
        unauthorized(TestJwtKeys.token(Map.of("alg", "none"), TestJwtKeys.claims(), TestJwtKeys.TRUSTED));
    }

    @Test
    void rejectsATokenWithAlgNoneHs256ExpiredOrWithoutSub_hs256() throws Exception {
        unauthorized(TestJwtKeys.token(Map.of("alg", "HS256"), TestJwtKeys.claims(), TestJwtKeys.TRUSTED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hs256", "RS512", ""})
    void rejectsEveryOtherAlgorithm(String algorithm) throws Exception {
        unauthorized(TestJwtKeys.token(Map.of("alg", algorithm), TestJwtKeys.claims(), TestJwtKeys.TRUSTED));
    }

    @Test
    void rejectsAMissingAlgorithm() throws Exception {
        unauthorized(TestJwtKeys.token(Map.of(), TestJwtKeys.claims(), TestJwtKeys.TRUSTED));
    }

    @Test
    void rejectsATokenWithAlgNoneHs256ExpiredOrWithoutSub_expired() throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("exp", TestJwtKeys.NOW.minusSeconds(31).getEpochSecond());
        unauthorized(TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED));
    }

    @Test
    void acceptsATokenExpiredTenSecondsAgo() throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("exp", TestJwtKeys.NOW.minusSeconds(10).getEpochSecond());
        withToken(TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() throws Exception {
        unauthorized(TestJwtKeys.token(Map.of("alg", "RS256"), TestJwtKeys.claims(), TestJwtKeys.OTHER));
    }

    @Test
    void rejectsAMissingExpiration() throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.remove("exp");
        unauthorized(TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"string", "fraction", "null"})
    void rejectsANonIntegerExpiration(String kind) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("exp", switch (kind) {
            case "string" -> "9999999999";
            case "fraction" -> 9999999999.5;
            default -> null;
        });
        unauthorized(TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED));
    }

    @Test
    void rejectsATokenWithAlgNoneHs256ExpiredOrWithoutSub_withoutSub() throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.remove("sub");
        unauthorized(TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED));
    }

    @Test
    void rejectsABlankSubject() throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("sub", " ");
        unauthorized(TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED));
    }

    @Test
    void rejectsBasicAuthorization() throws Exception {
        mvc.perform(get(PATH).header("Authorization", "Basic test"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsATwoPartToken() throws Exception {
        unauthorized("e30.e30");
    }

    @Test
    void aValidTokenAnswers200AndExposesItsPrincipal() throws Exception {
        withToken(TestJwtKeys.token()).andExpect(status().isOk());
        mvc.perform(get("/test-support/principal").header("Authorization", "Bearer " + TestJwtKeys.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sub").value(TestJwtKeys.ACCOUNT))
                .andExpect(jsonPath("$.tenantId").value(TestJwtKeys.TENANT))
                .andExpect(jsonPath("$.roles[0]").value("TENANT_ADMINISTRATOR"));
    }

    @Test
    void rejectsAnInvalidTenantId() throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("tenantId", "invalid-uuid");
        unauthorized(TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED));
    }

    @Test
    void acceptsAbsentTenantAndRoles() throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.remove("tenantId");
        claims.remove("roles");
        String token = TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
        mvc.perform(get("/test-support/principal").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value("null"))
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void acceptsANullTenant() throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("tenantId", null);
        withToken(TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsRolesThatAreNotAListOfStrings() throws Exception {
        for (Object roles : List.of("ADMIN", List.of(1), Map.of("role", "ADMIN"))) {
            Map<String, Object> claims = TestJwtKeys.claims();
            claims.put("roles", roles);
            unauthorized(TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED));
        }
    }

    @Test
    void unauthorizedEnvelopeNeverRevealsTheReason() throws Exception {
        String body = unauthorized("not.a.token").andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("InvalidTokenException", "base64", "signature", "expired", "stack");
    }

    @Test
    void aMalformedPathIdNamesItsField() throws Exception {
        mvc.perform(get("/test-support/ids/not-a-uuid").header("Authorization", "Bearer " + TestJwtKeys.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void invalidBodyAnswers400WithOneDetailPerField() throws Exception {
        mvc.perform(post("/test-support/domain").header("Authorization", "Bearer " + TestJwtKeys.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value("commercialName"))
                .andExpect(jsonPath("$.details[0].message").value("commercialName must not be blank"));
    }

    @Test
    void aMissingResourceAnswers404WithTheCommonEnvelope() throws Exception {
        mvc.perform(get("/test-support/missing").header("Authorization", "Bearer " + TestJwtKeys.token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").doesNotExist()).andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void unknownRouteAndMalformedJsonAnswerTheCommonEnvelope() throws Exception {
        mvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + TestJwtKeys.token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isString());
        mvc.perform(post("/test-support/json").header("Authorization", "Bearer " + TestJwtKeys.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("body"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void anUnexpectedExceptionNeverLeaksItsDetails() throws Exception {
        mvc.perform(get("/test-support/failure").header("Authorization", "Bearer " + TestJwtKeys.token())
                        .header("X-Correlation-Id", TRACE))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("an unexpected error occurred"))
                .andExpect(jsonPath("$.traceId").value(TRACE))
                .andExpect(header().string("X-Correlation-Id", TRACE))
                .andExpect(content().string(not(containsString("boom"))))
                .andExpect(content().string(not(containsString("secret detail"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void correlationIdIsReusedOrGeneratedAndReturned() throws Exception {
        mvc.perform(get(PATH).header("X-Correlation-Id", TRACE))
                .andExpect(status().isUnauthorized()).andExpect(header().string("X-Correlation-Id", TRACE))
                .andExpect(jsonPath("$.traceId").value(TRACE));
        String generated = withToken(TestJwtKeys.token()).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Correlation-Id");
        assertThat(UUID.fromString(generated)).isNotNull();
        mvc.perform(get(PATH).header("Authorization", "Bearer " + TestJwtKeys.token())
                        .header("X-Correlation-Id", TRACE))
                .andExpect(status().isOk()).andExpect(header().string("X-Correlation-Id", TRACE));
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void unsafeCorrelationIdsAreReplaced() throws Exception {
        for (String unsafe : List.of("with spaces", "x".repeat(200))) {
            var response = mvc.perform(get(PATH).header("X-Correlation-Id", unsafe))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse();
            String trace = response.getHeader("X-Correlation-Id");
            assertThat(UUID.fromString(trace)).isNotNull();
            assertThat(TestJwtKeys.JSON.readTree(response.getContentAsString()).path("traceId").asText())
                    .isEqualTo(trace);
        }
    }

    @Test
    void aDisallowedHttpMethodUsesNotFoundUntilTheContractDecides() throws Exception {
        mvc.perform(post(PATH).header("Authorization", "Bearer " + TestJwtKeys.token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void healthIsPublicOnlyForGet() throws Exception {
        mvc.perform(post("/health")).andExpect(status().isUnauthorized());
    }
}
