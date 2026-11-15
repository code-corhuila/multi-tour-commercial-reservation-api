package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// G-API8-1: the saga change-departure-vehicles reads the capacity of a tour with a SERVICE token, on the internal route with the tenant in the path. The public
// route keeps its roles. Every value is invented.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
class InternalCatalogHttpTest {
    static final String INTERNAL = "/api/v1/commercial-reservation/internal/tenants/";
    static final String PUBLIC = "/api/v1/commercial-reservation/catalog/services/";

    @Autowired MockMvc mvc;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;
    final UUID tenant = UUID.fromString(TestJwtKeys.TENANT);
    CatalogService item;

    @BeforeEach void plant() {
        catalog.items.clear();
        item = CatalogService.register(UUID.randomUUID(), tenant, CatalogServiceType.TOUR, "Tour Ficticio", CommercialTerms.of(100L, 50L, 18, true, false), TestJwtKeys.NOW);
        catalog.save(item);
    }

    private MockHttpServletRequestBuilder as(String url, String role) throws Exception {
        var request = get(url).header("X-Correlation-Id", "internal-catalog-test");
        return role == null ? request : request.header("Authorization", "Bearer " + CustomerHttpTest.token(null, role, List.of()));
    }

    @Test void aServiceTokenReadsAnItemOfTheTenantOfThePath() throws Exception {
        mvc.perform(as(INTERNAL + tenant + "/catalog/services/" + item.serviceId(), "SERVICE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(item.serviceId().toString()))
                .andExpect(jsonPath("$.configuredCapacity").value(18))
                .andExpect(jsonPath("$.catalogStatus").value("ACTIVE"));
    }

    @Test void anItemOfAnotherTenantOrAMissingOneIs404() throws Exception {
        mvc.perform(as(INTERNAL + UUID.randomUUID() + "/catalog/services/" + item.serviceId(), "SERVICE")).andExpect(status().isNotFound());
        mvc.perform(as(INTERNAL + tenant + "/catalog/services/" + UUID.randomUUID(), "SERVICE")).andExpect(status().isNotFound());
    }

    @Test void aPersonGets403AndNoTokenIs401() throws Exception {
        mvc.perform(as(INTERNAL + tenant + "/catalog/services/" + item.serviceId(), "TENANT_ADMINISTRATOR")).andExpect(status().isForbidden());
        mvc.perform(as(INTERNAL + tenant + "/catalog/services/" + item.serviceId(), "OPERATIONAL_COLLABORATOR")).andExpect(status().isForbidden());
        mvc.perform(as(INTERNAL + tenant + "/catalog/services/" + item.serviceId(), null)).andExpect(status().isUnauthorized());
    }

    @Test void theServiceTokenStillGets403OnThePublicRoute() throws Exception {
        mvc.perform(as(PUBLIC + item.serviceId(), "SERVICE")).andExpect(status().isForbidden());
    }

    @Test void aMalformedIdentifierIs400() throws Exception {
        mvc.perform(as(INTERNAL + tenant + "/catalog/services/not-a-uuid", "SERVICE")).andExpect(status().isBadRequest());
    }
}
