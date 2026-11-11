package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantIdPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ActiveTenantQueries;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-WRK-001 DP-08 over the internal route GET /internal/tenants: only the SERVICE token of a job reads it, a person gets 403, the answer is the
// envelope with ids and nothing else. The store is in memory here; the real query is proved in TenantStatusPersistenceTest. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, ActiveTenantsHttpTest.Memory.class})
class ActiveTenantsHttpTest {
    static final String BASE = "/api/v1/commercial-reservation/internal/tenants";

    @TestConfiguration
    static class Memory {
        @Bean @Primary MemoryTenants memoryActiveTenants() { return new MemoryTenants(); }
    }

    static final class MemoryTenants implements ActiveTenantQueries {
        final List<UUID> active = new ArrayList<>();
        public TenantIdPage page(int page, int limit) {
            int from = Math.min((page - 1) * limit, active.size());
            return new TenantIdPage(active.subList(from, Math.min(from + limit, active.size())), active.size());
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MemoryTenants tenants;
    UUID first, second, third;

    @BeforeEach void plant() {
        tenants.active.clear();
        first = new UUID(31, 1);
        second = new UUID(31, 2);
        third = new UUID(31, 3);
        tenants.active.addAll(List.of(first, second, third));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String role) throws Exception {
        request.header("X-Correlation-Id", "tenants-test");
        if (role == null) return request;
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of(role));
        return request.header("Authorization", "Bearer " + TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED));
    }

    @Test void aServiceTokenReadsThePagedIdsAndNothingElse() throws Exception {
        mvc.perform(as(get(BASE + "?status=ACTIVE&page=2&limit=2"), "SERVICE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tenantId").value(third.toString()))
                .andExpect(jsonPath("$.data[0].length()").value(1))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
        mvc.perform(as(get(BASE), "SERVICE")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test void aPersonOrNoTokenIsRefusedAndABadQueryIsA400() throws Exception {
        mvc.perform(as(get(BASE + "?status=ACTIVE"), "TENANT_ADMINISTRATOR")).andExpect(status().isForbidden());
        mvc.perform(as(get(BASE + "?status=ACTIVE"), "OPERATIONAL_COLLABORATOR")).andExpect(status().isForbidden());
        mvc.perform(as(get(BASE + "?status=ACTIVE"), null)).andExpect(status().isUnauthorized());
        mvc.perform(as(get(BASE + "?status=INACTIVE"), "SERVICE")).andExpect(status().isBadRequest());
        mvc.perform(as(get(BASE + "?limit=101"), "SERVICE")).andExpect(status().isBadRequest());
        mvc.perform(as(get(BASE + "?name=x"), "SERVICE")).andExpect(status().isBadRequest());
    }
}
