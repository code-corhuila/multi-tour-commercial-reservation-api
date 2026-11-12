package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ExceptionalAccessAudit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

// HU-SEC-007 G-SEC9-1 (ADR-004 D5): a token with access=exceptional or emergency is accepted only on a read inside its scope, is refused with 403 on any write, and every use that is let through is audited with
// the authorizationId (the port records it; the log adapter is the default until the outbox event is confirmed).
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, TenantStatusHttpFixture.class, ExceptionalAccessHttpTest.Recording.class})
class ExceptionalAccessHttpTest {
    static final String BASE = "/api/v1/commercial-reservation";
    static final UUID AUTHORIZATION = UUID.randomUUID();

    @TestConfiguration
    static class Recording {
        @Bean @Primary Uses uses() {
            return new Uses();
        }
    }

    static class Uses implements ExceptionalAccessAudit {
        final List<String> recorded = new ArrayList<>();

        @Override public void used(UUID authorizationId, String access, String area, String method, String routeTemplate) {
            recorded.add(authorizationId + "|" + access + "|" + area + "|" + method + "|" + routeTemplate);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired TenantStatusHttpFixture.MemoryStatus statuses;
    @Autowired Uses uses;

    @BeforeEach void clean() {
        statuses.clear();
        uses.recorded.clear();
    }

    private String token(String access, List<String> scope) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of("PLATFORM_ADMINISTRATOR"));
        claims.put("access", access);
        claims.put("scope", scope);
        claims.put("authorizationId", AUTHORIZATION.toString());
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private int status(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + bearer).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
    }

    @Test void aReadInsideTheScopeIsLetThroughAndAudited() throws Exception {
        // The mark of the token makes the actor read only (PLATFORM_SUPPORT): the route answers the read, and the use is audited.
        assertThat(status(get(BASE + "/catalog/services"), token("exceptional", List.of("CATALOG")))).isEqualTo(200);

        assertThat(uses.recorded).containsExactly(AUTHORIZATION + "|exceptional|CATALOG|GET|" + BASE + "/catalog/services");
    }

    @Test void thePlatformAdministratorWithoutTheMarkIsStillRefusedByRole() throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of("PLATFORM_ADMINISTRATOR"));
        String bearer = TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);

        assertThat(status(get(BASE + "/catalog/services"), bearer)).isEqualTo(403);
        assertThat(uses.recorded).isEmpty();
    }

    @Test void aReadOutsideTheScopeIsRefusedAndNotRecordedAsAUse() throws Exception {
        assertThat(status(get(BASE + "/catalog/services"), token("exceptional", List.of("RESERVATIONS")))).isEqualTo(403);
        assertThat(status(get(BASE + "/internal/outbox/events"), token("exceptional", List.of("RESERVATIONS", "CATALOG")))).isEqualTo(403);
        assertThat(uses.recorded).isEmpty();
    }

    @Test void anyWriteIsRefusedWithForbiddenEvenInsideTheScope() throws Exception {
        String bearer = token("emergency", List.of("RESERVATIONS", "CATALOG", "CUSTOMERS", "CASH"));

        assertThat(status(post(BASE + "/reservations"), bearer)).isEqualTo(403);
        assertThat(status(post(BASE + "/catalog/services"), bearer)).isEqualTo(403);
        assertThat(status(put(BASE + "/catalog/settings"), bearer)).isEqualTo(403);
        var body = mvc.perform(post(BASE + "/reservations").header("Authorization", "Bearer " + bearer).contentType(MediaType.APPLICATION_JSON).content("not even json"))
                .andReturn().getResponse();
        assertThat(body.getStatus()).isEqualTo(403);
        assertThat(body.getContentAsString()).contains("FORBIDDEN");
        assertThat(uses.recorded).isEmpty();
    }

    @Test void aTokenWithAnExceptionalAccessButNoScopeOrAuthorizationIsUnauthorized() throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of("PLATFORM_ADMINISTRATOR"));
        claims.put("access", "exceptional");
        String bearer = TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);

        assertThat(status(get(BASE + "/catalog/services"), bearer)).isEqualTo(401);
    }
}
