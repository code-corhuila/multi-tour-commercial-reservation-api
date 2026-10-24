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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-001 AC4 (AC-01.6, AC-01.7, RN-04, DD-19): when the tenant enabled the Operational Collaborator, they update the descriptive
// information and nothing else; every other change, and the creation and the status changes, stay with the Tenant Administrator.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
class CatalogCollaboratorEnabledHttpTest {
    @Autowired MockMvc mvc;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;
    @Autowired CatalogHttpFixture.MemoryCollaboratorPolicy policy;

    @Test void anEnabledCollaboratorRenamesTheItemAndNothingElseChanges() throws Exception {
        var item = plant(true);
        var before = read(item);
        var result = mvc.perform(update(item, Map.of("commercialName", "Renamed by the collaborator"))
                .header("Authorization", "Bearer " + token(item.tenantId().toString(), "OPERATIONAL_COLLABORATOR"))
                .header("X-Correlation-Id", TRACE)).andExpect(status().isOk()).andReturn();
        new CatalogContract().validate(result, false);
        var after = read(item);
        assertThat(after.get("commercialName").asText()).isEqualTo("Renamed by the collaborator");
        assertThat(after.get("baseRateCents")).isEqualTo(before.get("baseRateCents"));
        assertThat(after.get("baseOperationalCostCents")).isEqualTo(before.get("baseOperationalCostCents"));
        assertThat(after.get("configuredCapacity")).isEqualTo(before.get("configuredCapacity"));
        assertThat(after.get("catalogStatus")).isEqualTo(before.get("catalogStatus"));
    }

    @ParameterizedTest
    @CsvSource({"baseRateCents,200", "baseOperationalCostCents,75", "configuredCapacity,25"})
    void anEnabledCollaboratorCannotChangeRatesCostsOrCapacity(String field, long value) throws Exception {
        var item = plant(true);
        assertRefusedAndUnchanged(item, update(item, Map.of(field, value)));
    }

    @ParameterizedTest
    @CsvSource({"transportRequired,true", "highRisk,true"})
    void anEnabledCollaboratorCannotChangeTheStructuralFlags(String field, boolean value) throws Exception {
        var item = plant(true);
        assertRefusedAndUnchanged(item, update(item, Map.of(field, value)));
    }

    @Test void aMixedChangeIsRefusedAsAWholeAndTheNameStaysAsItWas() throws Exception {
        var item = plant(true);
        assertRefusedAndUnchanged(item, update(item, Map.of("commercialName", "Half applied", "baseRateCents", 200L)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactivate", "reactivate"})
    void anEnabledCollaboratorCannotChangeTheStatus(String operation) throws Exception {
        var item = plant(true);
        if (operation.equals("reactivate")) item.inactivate(UUID.randomUUID(), TestJwtKeys.NOW.minusSeconds(1));
        item.pullEvents();
        assertRefusedAndUnchanged(item, post(path(item) + "/" + operation));
    }

    @Test void anEnabledCollaboratorCannotCreateAnItem() throws Exception {
        var tenant = UUID.randomUUID();
        policy.enable(tenant);
        var result = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "collaborator-create-1")
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("serviceType", "TOUR", "commercialName", "Not allowed",
                        "baseRateCents", 100L, "baseOperationalCostCents", 50L, "configuredCapacity", 10, "transportRequired", false,
                        "highRisk", false)))
                .header("Authorization", "Bearer " + token(tenant.toString(), "OPERATIONAL_COLLABORATOR"))
                .header("X-Correlation-Id", TRACE)).andExpect(status().isForbidden()).andReturn();
        assertError(result, "FORBIDDEN", "access is forbidden");
    }

    @Test void theEnablingOfAnotherTenantDoesNotOpenThisOne() throws Exception {
        var item = plant(false);
        policy.enable(UUID.randomUUID());
        assertRefusedAndUnchanged(item, update(item, Map.of("commercialName", "Not my tenant")));
    }

    @Test void aMalformedBodyOfANonEnablingTenantAnswersForbiddenAndDisclosesNoValidation() throws Exception {
        var item = plant(false);
        var result = mvc.perform(patch(path(item)).contentType(MediaType.APPLICATION_JSON).content("{ not json")
                .header("Authorization", "Bearer " + token(item.tenantId().toString(), "OPERATIONAL_COLLABORATOR"))
                .header("X-Correlation-Id", TRACE)).andExpect(status().isForbidden()).andReturn();
        assertError(result, "FORBIDDEN", "access is forbidden");
    }

    private void assertRefusedAndUnchanged(CatalogService item, MockHttpServletRequestBuilder request) throws Exception {
        var before = read(item);
        var refused = mvc.perform(request.header("Authorization",
                "Bearer " + token(item.tenantId().toString(), "OPERATIONAL_COLLABORATOR"))
                .header("X-Correlation-Id", TRACE)).andExpect(status().isForbidden()).andReturn();
        new CatalogContract().validate(refused, false);
        assertError(refused, "FORBIDDEN", "access is forbidden");
        assertThat(read(item)).isEqualTo(before);
        assertThat(item.pullEvents()).isEmpty();
    }

    private JsonNode read(CatalogService item) throws Exception {
        var result = mvc.perform(get(path(item)).header("Authorization",
                "Bearer " + token(item.tenantId().toString(), "TENANT_ADMINISTRATOR")))
                .andExpect(status().isOk()).andReturn();
        return json(result);
    }

    private MockHttpServletRequestBuilder update(CatalogService item, Map<String, Object> changes) throws Exception {
        return patch(path(item)).contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(changes));
    }

    private CatalogService plant(boolean enabled) {
        var item = CatalogService.register(UUID.randomUUID(), UUID.randomUUID(), CatalogServiceType.TOUR,
                "Enabled collaborator tour", CommercialTerms.of(100L, 50L, 18, false, false), TestJwtKeys.NOW.minusSeconds(100));
        item.pullEvents();
        catalog.save(item);
        if (enabled) policy.enable(item.tenantId());
        return item;
    }

    private String path(CatalogService item) { return BASE + "/" + item.serviceId(); }
}
