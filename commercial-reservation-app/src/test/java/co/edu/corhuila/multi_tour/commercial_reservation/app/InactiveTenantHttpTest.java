package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

// api spec 021 T-05 (RN-TSC-08): the token of an inactive tenant is refused on the person routes and nowhere else.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, TenantStatusHttpFixture.class})
class InactiveTenantHttpTest {
    static final String PERSON_ROUTE = "/api/v1/commercial-reservation/catalog/services";
    static final UUID TENANT = UUID.fromString(TestJwtKeys.TENANT);
    @Autowired MockMvc mvc;
    @Autowired TenantStatusHttpFixture.MemoryStatus statuses;

    @BeforeEach void clear() { statuses.clear(); }

    String token(String role, Map<String, Object> extra) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of(role));
        claims.putAll(extra);
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    int person(String bearer) throws Exception {
        return mvc.perform(get(PERSON_ROUTE).header("Authorization", "Bearer " + bearer)).andReturn().getResponse().getStatus();
    }

    @Test void aTokenOfAnInactiveTenantIs401UnlessRestricted() throws Exception {
        String plain = token("TENANT_ADMINISTRATOR", Map.of());
        String restricted = token("TENANT_ADMINISTRATOR", Map.of("access", "restricted"));
        statuses.statuses.put(TENANT, TenantStatus.ACTIVE);
        assertThat(person(plain)).isEqualTo(200);

        statuses.statuses.put(TENANT, TenantStatus.INACTIVE);
        assertThat(person(plain)).isEqualTo(401);
        assertThat(person(restricted)).isEqualTo(200);

        statuses.statuses.put(TENANT, TenantStatus.ACTIVE);
        assertThat(person(plain)).isEqualTo(200);
    }

    @Test void healthAndTheReceivingRouteAreOutsideTheCheck() throws Exception {
        statuses.statuses.put(TENANT, TenantStatus.INACTIVE);
        mvc.perform(get("/health")).andExpect(status().isOk());
        UUID id = UUID.randomUUID();
        String body = TestJwtKeys.JSON.writeValueAsString(TenantEventHttpTest.event("TenantCreated", id, "2026-10-29T14:05:00Z"));
        mvc.perform(post(TenantEventHttpTest.ROUTE).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Idempotency-Key", id.toString())
                .header("Authorization", "Bearer " + token("SERVICE", Map.of()))).andExpect(status().isCreated());
    }

    @Test void aTokenOfATenantWithNoCopyPasses() throws Exception {
        assertThat(person(token("TENANT_ADMINISTRATOR", Map.of()))).isEqualTo(200);
    }

    @Test void aFailingLookupIs500NotAPass() throws Exception {
        statuses.failure = new IllegalStateException("database down");

        assertThat(person(token("TENANT_ADMINISTRATOR", Map.of()))).isEqualTo(500);
        mvc.perform(get("/health")).andExpect(status().isOk());
    }
}
