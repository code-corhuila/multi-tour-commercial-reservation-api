package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-006 T-01 / T-06 (plan D-04): a method other than GET, or a path under /public/ that is not listed, answers the
// shared 404 before any token check, with or without a token. It never answers 401 and never changes anything.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
class PublicPathsHttpTest {
    static final String PUBLIC = "/api/v1/commercial-reservation/public/tenants/" + TestJwtKeys.TENANT;
    @Autowired MockMvc mvc;

    private int status(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test void anyMethodOtherThanGetIsRefusedWithoutAToken() throws Exception {
        String id = UUID.randomUUID().toString();
        for (String path : new String[] {"/tours", "/tours/" + id, "/associated-establishments",
                "/associated-establishments/" + id, "/associated-establishments/" + id + "/image"}) {
            assertThat(status(post(PUBLIC + path))).as("POST " + path).isEqualTo(404);
            assertThat(status(put(PUBLIC + path))).as("PUT " + path).isEqualTo(404);
            assertThat(status(patch(PUBLIC + path))).as("PATCH " + path).isEqualTo(404);
            assertThat(status(delete(PUBLIC + path))).as("DELETE " + path).isEqualTo(404);
        }
    }

    @Test void theRefusalIsTheSharedNotFoundEnvelope() throws Exception {
        var result = mvc.perform(post(PUBLIC + "/tours")).andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(404);
        assertThat(result.getContentAsString()).contains("\"error\":\"NOT_FOUND\"");
        assertThat(result.getHeader("Cache-Control")).isNull();
    }

    @Test void aPathUnderPublicThatIsNotListedAnswersNotFoundNotUnauthorized() throws Exception {
        assertThat(status(get(PUBLIC + "/other"))).isEqualTo(404);
        assertThat(status(get(PUBLIC + "/tours/" + UUID.randomUUID() + "/departures"))).isEqualTo(404);
        assertThat(status(get("/api/v1/commercial-reservation/public/tenants"))).isEqualTo(404);
    }

    @Test void thePathsOutsideThePublicAreaStillNeedAToken() throws Exception {
        assertThat(status(get("/api/v1/commercial-reservation/catalog/services"))).isEqualTo(401);
        assertThat(status(post("/api/v1/commercial-reservation/associated-establishments"))).isEqualTo(401);
    }
}
