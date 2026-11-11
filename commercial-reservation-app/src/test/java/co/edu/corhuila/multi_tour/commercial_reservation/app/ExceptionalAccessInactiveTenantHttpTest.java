package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

// HU-SEC-007 (Project Lead, 2026-11-11): the token of an exceptional or emergency access reads only, so it is not answered 401 because the tenant is inactive (PDR section 9: the access still needs the
// approval of an Administrator, and the support work is a read); any ordinary token of an inactive tenant is still answered 401.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, TenantStatusHttpFixture.class})
class ExceptionalAccessInactiveTenantHttpTest {
    static final String BASE = "/api/v1/commercial-reservation";
    static final UUID TENANT = UUID.fromString(TestJwtKeys.TENANT);
    @Autowired MockMvc mvc;
    @Autowired TenantStatusHttpFixture.MemoryStatus statuses;

    @BeforeEach void inactive() {
        statuses.clear();
        statuses.statuses.put(TENANT, TenantStatus.INACTIVE);
    }

    private int read(String role, String access) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of(role));
        if (access != null) {
            claims.put("access", access);
            claims.put("scope", List.of("CATALOG"));
            claims.put("authorizationId", UUID.randomUUID().toString());
        }
        String token = TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
        return mvc.perform(get(BASE + "/catalog/services").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    @Test void anExceptionalOrEmergencyTokenIsNotUnauthorizedBecauseTheTenantIsInactive() throws Exception {
        assertThat(read("PLATFORM_ADMINISTRATOR", "exceptional")).isNotEqualTo(401);
        assertThat(read("PLATFORM_ADMINISTRATOR", "emergency")).isNotEqualTo(401);
    }

    @Test void anOrdinaryTokenOfAnInactiveTenantIsStillUnauthorized() throws Exception {
        assertThat(read("TENANT_ADMINISTRATOR", null)).isEqualTo(401);
        assertThat(read("PLATFORM_ADMINISTRATOR", null)).isEqualTo(401);
    }
}
