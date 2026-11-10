package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// T-07 of HU-COM-020 (-api): RN-11; AC-20.14. The token of the Administrator of an inactive tenant (access=restricted) is refused with 403 FORBIDDEN before any controller on every route that
// creates or changes something outside the regularization work (a new reservation, a quote, a payment, the catalog, the settings), and only an Administrator can use it; an ordinary token of
// an active tenant is not touched by the policy.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, TenantEventHttpFixture.class, TenantStatusHttpFixture.class})
class RestrictedAccessHttpTest {
    static final String BASE = "/api/v1/commercial-reservation";
    static final UUID TENANT = UUID.fromString(TestJwtKeys.TENANT);
    @Autowired MockMvc mvc;
    @Autowired TenantStatusHttpFixture.MemoryStatus statuses;

    @BeforeEach void inactive() {
        statuses.clear();
        statuses.statuses.put(TENANT, TenantStatus.INACTIVE);
    }

    private String token(String role, boolean restricted) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.put("roles", List.of(role));
        if (restricted) claims.put("access", "restricted");
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private int status(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + bearer).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
    }

    @Test void aRestrictedTokenCannotCreateAReservationOrAPaymentOrChangeTheCatalog() throws Exception {
        String restricted = token("TENANT_ADMINISTRATOR", true);
        UUID id = UUID.randomUUID();

        assertThat(status(post(BASE + "/reservations"), restricted)).isEqualTo(403);
        assertThat(status(post(BASE + "/reservation-quotes"), restricted)).isEqualTo(403);
        assertThat(status(post(BASE + "/reservations/" + id + "/payments"), restricted)).isEqualTo(403);
        assertThat(status(post(BASE + "/catalog/services"), restricted)).isEqualTo(403);
        assertThat(status(put(BASE + "/catalog/settings"), restricted)).isEqualTo(403);
        assertThat(status(post(BASE + "/discount-rules"), restricted)).isEqualTo(403);
    }

    @Test void aRestrictedTokenOfAnotherRoleIsRefusedEvenOnARead() throws Exception {
        assertThat(status(get(BASE + "/catalog/services"), token("OPERATIONAL_COLLABORATOR", true))).isEqualTo(403);
        assertThat(status(get(BASE + "/catalog/services"), token("TENANT_ADMINISTRATOR", true))).isEqualTo(200);
    }

    @Test void theBodyIsNotReadAndNothingIsWrittenWhenTheRouteIsDenied() throws Exception {
        var result = mvc.perform(post(BASE + "/reservations").header("Authorization", "Bearer " + token("TENANT_ADMINISTRATOR", true))
                .contentType(MediaType.APPLICATION_JSON).content("not even json")).andReturn().getResponse();

        assertThat(result.getStatus()).isEqualTo(403);
        assertThat(result.getContentAsString()).contains("FORBIDDEN");
    }

    @Test void anOrdinaryTokenOfAnActiveTenantIsNotTouchedByThePolicy() throws Exception {
        statuses.statuses.put(TENANT, TenantStatus.ACTIVE);

        assertThat(status(get(BASE + "/catalog/services"), token("TENANT_ADMINISTRATOR", false))).isEqualTo(200);
        assertThat(status(post(BASE + "/catalog/services"), token("TENANT_ADMINISTRATOR", false))).isNotEqualTo(403);
    }
}
