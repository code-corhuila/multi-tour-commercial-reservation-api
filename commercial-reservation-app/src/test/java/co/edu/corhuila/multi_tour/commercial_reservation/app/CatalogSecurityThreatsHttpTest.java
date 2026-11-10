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

// The three checks of the gateway-to-api threat model of the catalog (issues 581, 582 and 583 of multi-tour-docs), on the real catalog routes and without a database:
// every entry point that changes what the tenant offers is refused to every role but the Tenant Administrator (E, 581); an expired, badly signed or wrong-algorithm token
// is a 401 on those routes and changes nothing (S, 582); an item of another tenant answers exactly as a missing one and discloses no stack trace or SQL (I, 583).
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
class CatalogSecurityThreatsHttpTest {
    private static final List<String> REFUSED_ROLES = List.of("OPERATIONAL_COLLABORATOR", "MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER");

    @Autowired MockMvc mvc;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;
    @Autowired CatalogHttpFixture.MemoryCollaboratorPolicy policy;

    // 581: the Collaborator is enabled for descriptive updates in this tenant, which must not open the creation or the status changes.
    @Test void onlyTheTenantAdministratorCreatesAnItem() throws Exception {
        var tenant = UUID.randomUUID();
        policy.enable(tenant);
        int before = catalog.items.size();
        for (String role : REFUSED_ROLES) {
            assertForbidden(mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "threat-create-" + role)
                    .content(TestJwtKeys.JSON.writeValueAsBytes(CatalogHttpTest.body())).header("Authorization", "Bearer " + token(tenant.toString(), role))
                    .header("X-Correlation-Id", TRACE)).andReturn());
        }
        assertThat(catalog.items).hasSize(before);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "threat-create-admin")
                .content(TestJwtKeys.JSON.writeValueAsBytes(CatalogHttpTest.body())).header("Authorization", "Bearer " + token(tenant.toString(), "TENANT_ADMINISTRATOR"))
                .header("X-Correlation-Id", TRACE)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        assertThat(catalog.items).hasSize(before + 1);
    }

    @Test void onlyTheTenantAdministratorInactivatesAnItem() throws Exception {
        var item = plant(true);
        for (String role : REFUSED_ROLES) assertRefusedAndUnchanged(item, post(path(item) + "/inactivate"), role);
        mvc.perform(post(path(item) + "/inactivate").header("Authorization", "Bearer " + token(item.tenantId().toString(), "TENANT_ADMINISTRATOR"))
                .header("X-Correlation-Id", TRACE)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(catalog.items.get(item.serviceId()).catalogStatus().name()).isEqualTo("INACTIVE");
    }

    @Test void onlyTheTenantAdministratorReactivatesAnItem() throws Exception {
        var item = plant(true);
        item.inactivate(UUID.randomUUID(), TestJwtKeys.NOW.minusSeconds(1));
        item.pullEvents();
        for (String role : REFUSED_ROLES) assertRefusedAndUnchanged(item, post(path(item) + "/reactivate"), role);
        mvc.perform(post(path(item) + "/reactivate").header("Authorization", "Bearer " + token(item.tenantId().toString(), "TENANT_ADMINISTRATOR"))
                .header("X-Correlation-Id", TRACE)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(catalog.items.get(item.serviceId()).catalogStatus().name()).isEqualTo("ACTIVE");
    }

    // 582: valid RSA signatures isolate the algorithm check from the signature check; every case is refused on a read route and on a write route, and the write changes nothing.
    @ParameterizedTest
    @ValueSource(strings = {"expired", "otherKey", "RS512", "HS256", "none"})
    void anInvalidTokenIsAnswered401OnTheCatalogRoutesAndChangesNothing(String flaw) throws Exception {
        var item = plant(false);
        String bad = invalid(flaw, item.tenantId().toString());

        assertUnauthorized(mvc.perform(get(path(item)).header("Authorization", "Bearer " + bad).header("X-Correlation-Id", TRACE)).andReturn());
        assertUnauthorized(mvc.perform(post(path(item) + "/inactivate").header("Authorization", "Bearer " + bad).header("X-Correlation-Id", TRACE)).andReturn());
        assertThat(catalog.items.get(item.serviceId()).catalogStatus().name()).isEqualTo("ACTIVE");
        assertThat(item.pullEvents()).isEmpty();
    }

    // 583: the item of tenant A asked with a token of tenant B answers as an id that does not exist, on every route that takes the id, with no internals in the body.
    @Test void anItemOfAnotherTenantAnswersExactlyAsAMissingOneOnEveryRouteWithoutInternals() throws Exception {
        var item = plant(false);
        String stranger = UUID.randomUUID().toString();
        List<java.util.function.Function<UUID, MockHttpServletRequestBuilder>> routes = List.of(
                id -> get(BASE + "/" + id),
                id -> patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"commercialName\":\"Other name\"}"),
                id -> post(BASE + "/" + id + "/inactivate"),
                id -> post(BASE + "/" + id + "/reactivate"));
        for (var route : routes) {
            MvcResult foreign = mvc.perform(route.apply(item.serviceId()).header("Authorization", "Bearer " + token(stranger, "TENANT_ADMINISTRATOR"))
                    .header("X-Correlation-Id", TRACE)).andReturn();
            MvcResult missing = mvc.perform(route.apply(UUID.randomUUID()).header("Authorization", "Bearer " + token(stranger, "TENANT_ADMINISTRATOR"))
                    .header("X-Correlation-Id", TRACE)).andReturn();
            assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
            assertThat(missing.getResponse().getStatus()).isEqualTo(404);
            assertThat(json(foreign)).isEqualTo(json(missing));
            assertThat(foreign.getResponse().getContentType()).isEqualTo(missing.getResponse().getContentType());
            String body = foreign.getResponse().getContentAsString();
            assertThat(body).doesNotContain(item.serviceId().toString(), item.tenantId().toString(), item.commercialName());
            assertThat(body).doesNotContainIgnoringCase("exception", "stack", "\tat ", "select ", "insert ", "sql", "org.", "java.", "co.edu");
        }
        assertThat(catalog.items.get(item.serviceId()).catalogStatus().name()).isEqualTo("ACTIVE");
        assertThat(catalog.items.get(item.serviceId()).commercialName()).isEqualTo(item.commercialName());
    }

    private String invalid(String flaw, String tenant) throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("tenantId", tenant); claims.put("roles", List.of("TENANT_ADMINISTRATOR"));
        return switch (flaw) {
            case "expired" -> {
                claims.put("exp", TestJwtKeys.NOW.minusSeconds(31).getEpochSecond());
                yield TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
            }
            case "otherKey" -> TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.OTHER);
            default -> TestJwtKeys.token(Map.of("alg", flaw), claims, TestJwtKeys.TRUSTED);
        };
    }

    private void assertUnauthorized(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertError(result, "UNAUTHORIZED", "authentication is required");
    }

    private void assertForbidden(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertError(result, "FORBIDDEN", "access is forbidden");
    }

    private void assertRefusedAndUnchanged(CatalogService item, MockHttpServletRequestBuilder request, String role) throws Exception {
        var status = catalog.items.get(item.serviceId()).catalogStatus();
        assertForbidden(mvc.perform(request.header("Authorization", "Bearer " + token(item.tenantId().toString(), role)).header("X-Correlation-Id", TRACE)).andReturn());
        assertThat(catalog.items.get(item.serviceId()).catalogStatus()).isEqualTo(status);
        assertThat(item.pullEvents()).isEmpty();
    }

    private CatalogService plant(boolean collaboratorEnabled) {
        var item = CatalogService.register(UUID.randomUUID(), UUID.randomUUID(), CatalogServiceType.TOUR, "Threat model tour",
                CommercialTerms.of(100L, 50L, 18, false, false), TestJwtKeys.NOW.minusSeconds(100));
        item.pullEvents();
        catalog.save(item);
        if (collaboratorEnabled) policy.enable(item.tenantId());
        return item;
    }

    private String path(CatalogService item) { return BASE + "/" + item.serviceId(); }
}
