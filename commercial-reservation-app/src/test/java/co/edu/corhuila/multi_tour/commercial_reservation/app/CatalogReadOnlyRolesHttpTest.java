package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.BASE;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.TRACE;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.assertError;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.json;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
class CatalogReadOnlyRolesHttpTest {
    // AC-01.11 and RN-07 (DD-19 closed 2026-10-28): Manager and Analyst read the catalog; the Accountant is denied
    @Autowired MockMvc mvc;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ANALYST"})
    void managerAndAnalystReadTheListAndTheDetail(String role) throws Exception {
        var item = plant();
        var detail = mvc.perform(get(path(item)).header("Authorization", bearer(item, role))
                .header("X-Correlation-Id", TRACE)).andExpect(status().isOk()).andReturn();
        new CatalogContract().validate(detail, false);
        assertThat(json(detail).get("serviceId").asText()).isEqualTo(item.serviceId().toString());
        var list = mvc.perform(get(BASE).header("Authorization", bearer(item, role))
                .header("X-Correlation-Id", TRACE)).andExpect(status().isOk()).andReturn();
        new CatalogContract().validate(list, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ANALYST"})
    void managerAndAnalystCannotCreateUpdateOrInactivate(String role) throws Exception {
        var item = plant();
        assertForbidden(item, role, post(BASE).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "read-only-key")
                .content(TestJwtKeys.JSON.writeValueAsBytes(CatalogHttpTest.body())));
        assertForbidden(item, role, patch(path(item)).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("commercialName", "Changed"))));
        assertForbidden(item, role, post(path(item) + "/inactivate"));
    }

    @Test void accountantIsDenied() throws Exception {
        var item = plant();
        assertForbidden(item, "ACCOUNTANT", get(path(item)));
        assertForbidden(item, "ACCOUNTANT", get(BASE));
    }

    private void assertForbidden(CatalogService item, String role, MockHttpServletRequestBuilder request)
            throws Exception {
        var refused = mvc.perform(request.header("Authorization", bearer(item, role))
                .header("X-Correlation-Id", TRACE)).andExpect(status().isForbidden()).andReturn();
        new CatalogContract().validate(refused, false);
        assertError(refused, "FORBIDDEN", "access is forbidden");
        assertThat(item.pullEvents()).isEmpty();
    }

    private String bearer(CatalogService item, String role) throws Exception {
        return "Bearer " + token(item.tenantId().toString(), role);
    }

    private CatalogService plant() {
        var item = CatalogService.register(UUID.randomUUID(), UUID.randomUUID(), CatalogServiceType.TOUR,
                "Read-only roles tour", CommercialTerms.of(100L, 50L, 18, false, false),
                TestJwtKeys.NOW.minusSeconds(100));
        item.pullEvents();
        catalog.save(item);
        return item;
    }

    private String path(CatalogService item) { return BASE + "/" + item.serviceId(); }
}
