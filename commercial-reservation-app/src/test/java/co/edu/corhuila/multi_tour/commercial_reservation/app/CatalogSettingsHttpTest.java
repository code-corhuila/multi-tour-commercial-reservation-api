package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.TRACE;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.assertError;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.json;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-001 T-05 (RF-004, decision of 2026-10-30, PROPOSED contract GET and PUT /catalog/settings): the Tenant Administrator reads
// and sets the collaborator-editing flag, nobody else does, and the flag really opens the descriptive update of RN-04 (AC-01.6, AC-01.7).
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
class CatalogSettingsHttpTest {
    static final String SETTINGS = "/api/v1/commercial-reservation/catalog/settings";
    static final String ITEMS = "/api/v1/commercial-reservation/catalog/services";
    @Autowired MockMvc mvc;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;

    @Test void aTenantThatNeverSetTheFlagReadsItOffWithNoAuthor() throws Exception {
        var body = json(send(get(SETTINGS), UUID.randomUUID(), "TENANT_ADMINISTRATOR", 200));
        assertThat(body.get("collaboratorEditing").asBoolean(true)).isFalse();
        assertThat(body.get("updatedAt").isNull()).isTrue();
        assertThat(body.get("updatedBy").isNull()).isTrue();
    }

    @Test void theAdministratorSetsTheFlagAndReadsItBackWithTheirOwnIdentifierAsAuthor() throws Exception {
        var tenant = UUID.randomUUID();
        var written = json(send(set(true), tenant, "TENANT_ADMINISTRATOR", 200));
        assertThat(written.get("collaboratorEditing").asBoolean()).isTrue();
        assertThat(written.get("updatedBy").asText()).isEqualTo(TestJwtKeys.ACCOUNT);
        assertThat(written.get("updatedAt").asText()).isEqualTo(TestJwtKeys.NOW.toString());
        assertThat(json(send(get(SETTINGS), tenant, "TENANT_ADMINISTRATOR", 200))).isEqualTo(written);
    }

    @Test void thePutIsIdempotentSoTheSameValueTwiceAnswersTheSameBody() throws Exception {
        var tenant = UUID.randomUUID();
        var first = json(send(set(true), tenant, "TENANT_ADMINISTRATOR", 200));
        var second = json(send(set(true), tenant, "TENANT_ADMINISTRATOR", 200));
        assertThat(second).isEqualTo(first);
        assertThat(flag(tenant)).isTrue();
        assertThat(json(send(set(false), tenant, "TENANT_ADMINISTRATOR", 200)).get("collaboratorEditing").asBoolean(true)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATIONAL_COLLABORATOR", "MANAGER", "ANALYST", "ACCOUNTANT"})
    void noRoleButTheAdministratorReadsOrSetsTheFlag(String role) throws Exception {
        var tenant = UUID.randomUUID();
        assertError(send(get(SETTINGS), tenant, role, 403), "FORBIDDEN", "access is forbidden");
        assertError(send(set(true), tenant, role, 403), "FORBIDDEN", "access is forbidden");
        assertThat(flag(tenant)).isFalse();
    }

    @Test void theBodyCarriesNoUserSoAnyFieldButTheFlagIsRefused() throws Exception {
        var tenant = UUID.randomUUID();
        send(body(Map.of("collaboratorEditing", true, "updatedBy", UUID.randomUUID().toString())), tenant, "TENANT_ADMINISTRATOR", 400);
        assertThat(flag(tenant)).isFalse();
    }

    @Test void aMissingOrNonBooleanFlagIsRefusedAsAValidationErrorAndNothingChanges() throws Exception {
        var tenant = UUID.randomUUID();
        for (Map<String, Object> bad : List.of(Map.<String, Object>of(), Map.<String, Object>of("collaboratorEditing", "yes"),
                Map.<String, Object>of("collaboratorEditing", 1))) {
            var result = send(body(bad), tenant, "TENANT_ADMINISTRATOR", 400);
            assertThat(json(result).get("error").asText()).isEqualTo("VALIDATION_ERROR");
        }
        assertThat(flag(tenant)).isFalse();
    }

    @Test void theFlagOfOneTenantNeverReachesAnother() throws Exception {
        send(set(true), UUID.randomUUID(), "TENANT_ADMINISTRATOR", 200);
        assertThat(flag(UUID.randomUUID())).isFalse();
    }

    @Test void onceTheAdministratorEnablesTheFlagTheCollaboratorRenamesButCannotChangeRates() throws Exception {
        var item = plant();
        send(rename(item), item.tenantId(), "OPERATIONAL_COLLABORATOR", 403);
        send(set(true), item.tenantId(), "TENANT_ADMINISTRATOR", 200);
        send(rename(item), item.tenantId(), "OPERATIONAL_COLLABORATOR", 200);
        send(patch(ITEMS + "/" + item.serviceId()).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("baseRateCents", 999))),
                item.tenantId(), "OPERATIONAL_COLLABORATOR", 403);
        send(set(false), item.tenantId(), "TENANT_ADMINISTRATOR", 200);
        send(rename(item), item.tenantId(), "OPERATIONAL_COLLABORATOR", 403);
    }

    private boolean flag(UUID tenant) throws Exception {
        return json(send(get(SETTINGS), tenant, "TENANT_ADMINISTRATOR", 200)).get("collaboratorEditing").asBoolean(true);
    }

    private MvcResult send(MockHttpServletRequestBuilder request, UUID tenant, String role, int code) throws Exception {
        var result = mvc.perform(request.header("Authorization", "Bearer " + token(tenant.toString(), role))
                .header("X-Correlation-Id", TRACE)).andReturn();
        status().is(code).match(result);
        return result;
    }

    private MockHttpServletRequestBuilder body(Map<String, Object> content) throws Exception {
        return put(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(content));
    }

    private MockHttpServletRequestBuilder set(boolean enabled) throws Exception {
        return body(Map.of("collaboratorEditing", enabled));
    }

    private MockHttpServletRequestBuilder rename(CatalogService item) throws Exception {
        return patch(ITEMS + "/" + item.serviceId()).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("commercialName", "Renamed")));
    }

    private CatalogService plant() {
        var item = CatalogService.register(UUID.randomUUID(), UUID.randomUUID(), CatalogServiceType.TOUR, "Flag tour",
                CommercialTerms.of(100L, 50L, 18, false, false), TestJwtKeys.NOW.minusSeconds(100));
        item.pullEvents();
        catalog.save(item);
        return item;
    }
}
