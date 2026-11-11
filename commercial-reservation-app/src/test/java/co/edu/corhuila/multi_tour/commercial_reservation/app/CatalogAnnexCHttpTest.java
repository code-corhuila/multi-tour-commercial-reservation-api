package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.BASE;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.TRACE;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.assertError;
import static co.edu.corhuila.multi_tour.commercial_reservation.app.CatalogHttpTest.identifiers;
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
import org.junit.jupiter.api.BeforeEach;
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
class CatalogAnnexCHttpTest {
    @Autowired MockMvc mvc;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;
    @Autowired CatalogHttpFixture.MemoryKeys keys;

    @BeforeEach void clear() {
        catalog.items.clear();
        keys.keys.clear();
    }

    // A forbidden response must not become an oracle for another tenant's identifiers.
    @ParameterizedTest
    @ValueSource(strings = {"GET", "PATCH", "inactivate", "reactivate"})
    void missingPermissionAnswers403(String operation) throws Exception {
        UUID tenant = UUID.randomUUID();
        var own = plant(1, tenant);
        var foreign = plant(2, UUID.randomUUID());
        String bearer = "Bearer " + token(tenant.toString(), "OTHER");
        JsonNode first = null;
        for (UUID id : new UUID[] {own.serviceId(), foreign.serviceId(), UUID.randomUUID()}) {
            MockHttpServletRequestBuilder request;
            if (operation.equals("GET")) {
                request = get(BASE + "/" + id);
            } else if (operation.equals("PATCH")) {
                request = patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(TestJwtKeys.JSON.writeValueAsBytes(Map.of("highRisk", true)));
            } else {
                request = post(BASE + "/" + id + "/" + operation);
            }
            var result = mvc.perform(request.header("Authorization", bearer)
                    .header("X-Correlation-Id", TRACE)).andExpect(status().isForbidden()).andReturn();
            new CatalogContract().validate(result, false);
            assertError(result, "FORBIDDEN", "access is forbidden");
            var response = json(result);
            if (first == null) first = response;
            else assertThat(response).isEqualTo(first);
        }
        assertThat(catalog.items).hasSize(2);
        assertThat(own.terms().highRisk()).isFalse();
        assertThat(foreign.terms().highRisk()).isFalse();
        assertThat(own.pullEvents()).isEmpty();
        assertThat(foreign.pullEvents()).isEmpty();
    }

    // A nondefault limit detects an implementation that always returns the default page size.
    @Test void listingHonorsTheRequestedPageAndLimit() throws Exception {
        UUID tenant = UUID.randomUUID();
        for (int index = 1; index <= 8; index++) plant(index, tenant);
        var result = mvc.perform(get(BASE).param("page", "2").param("limit", "3")
                .header("Authorization", "Bearer " + token(tenant.toString(), "TENANT_ADMINISTRATOR"))
                .header("X-Correlation-Id", TRACE)).andExpect(status().isOk()).andReturn();
        new CatalogContract().validate(result, false);
        var response = json(result);
        assertThat(identifiers(response.get("data"))).hasSize(3).containsExactly(
                new UUID(0, 5).toString(), new UUID(0, 4).toString(), new UUID(0, 3).toString());
        assertThat(response.get("meta")).isEqualTo(TestJwtKeys.JSON.readTree(
                "{\"page\":2,\"limit\":3,\"total\":8,\"totalPages\":3}"));
    }

    @Test void unknownRouteAnswers404WithTheCommonEnvelope() throws Exception {
        var result = mvc.perform(get("/api/v1/annex-c-missing-route")
                .header("Authorization", "Bearer " + token(TestJwtKeys.TENANT, "TENANT_ADMINISTRATOR"))
                .header("X-Correlation-Id", TRACE)).andExpect(status().isNotFound()).andReturn();
        // An undeclared route has no operation to validate, but still owes the common envelope.
        assertError(result, "NOT_FOUND", "resource not found");
    }

    private CatalogService plant(int index, UUID tenant) {
        var item = CatalogService.register(new UUID(0, index), tenant, CatalogServiceType.TOUR,
                "Item " + index, CommercialTerms.of(100L, 50L, 18, true, false),
                TestJwtKeys.NOW.minusSeconds(100 - index));
        catalog.save(item);
        return item;
    }
}
