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
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class CatalogCollaboratorDenialHttpTest {
    // A tenant that never set the flag has it off: the Collaborator is refused on every change; these tests pin that behavior and the nothing-changes guarantee
    @Autowired MockMvc mvc;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;

    @ParameterizedTest
    @CsvSource({"baseRateCents,200", "baseOperationalCostCents,75", "configuredCapacity,25"})
    void collaboratorCannotChangeRatesCostsOrCapacity(String field, long value) throws Exception {
        var item = plant(false);
        assertRefusedAndUnchanged(item, update(item, Map.of(field, value)));
    }

    @Test void collaboratorOfNonEnablingTenantCannotUpdate() throws Exception {
        var active = plant(false);
        assertRefusedAndUnchanged(active, update(active, Map.of("commercialName", "Changed name")));
        assertRefusedAndUnchanged(active, update(active, Map.of("baseRateCents", 200L)));
        assertRefusedAndUnchanged(active, post(path(active) + "/inactivate"));
        // Reactivation must start from INACTIVE so an unauthorized transition could change the item.
        var inactive = plant(true);
        assertRefusedAndUnchanged(inactive, post(path(inactive) + "/reactivate"));
        assertThat(read(active, "OPERATIONAL_COLLABORATOR")).isEqualTo(read(active, "TENANT_ADMINISTRATOR"));
        assertThat(read(inactive, "OPERATIONAL_COLLABORATOR")).isEqualTo(read(inactive, "TENANT_ADMINISTRATOR"));
    }

    private void assertRefusedAndUnchanged(CatalogService item, MockHttpServletRequestBuilder request)
            throws Exception {
        var before = read(item, "TENANT_ADMINISTRATOR");
        var refused = mvc.perform(request.header("Authorization",
                "Bearer " + token(item.tenantId().toString(), "OPERATIONAL_COLLABORATOR"))
                .header("X-Correlation-Id", TRACE)).andExpect(status().isForbidden()).andReturn();
        new CatalogContract().validate(refused, false);
        assertError(refused, "FORBIDDEN", "access is forbidden");
        assertThat(read(item, "TENANT_ADMINISTRATOR")).isEqualTo(before);
        assertThat(item.pullEvents()).isEmpty();
    }

    private JsonNode read(CatalogService item, String role) throws Exception {
        var result = mvc.perform(get(path(item)).header("Authorization",
                "Bearer " + token(item.tenantId().toString(), role)))
                .andExpect(status().isOk()).andReturn();
        new CatalogContract().validate(result, false);
        return json(result);
    }

    private MockHttpServletRequestBuilder update(CatalogService item, Map<String, Object> changes)
            throws Exception {
        return patch(path(item)).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(changes));
    }

    private CatalogService plant(boolean inactive) {
        var item = CatalogService.register(UUID.randomUUID(), UUID.randomUUID(), CatalogServiceType.TOUR,
                "Collaborator denial tour", CommercialTerms.of(100L, 50L, 18, false, false),
                TestJwtKeys.NOW.minusSeconds(100));
        if (inactive) item.inactivate(UUID.randomUUID(), TestJwtKeys.NOW.minusSeconds(1));
        item.pullEvents();
        catalog.save(item);
        return item;
    }

    private String path(CatalogService item) { return BASE + "/" + item.serviceId(); }
}
