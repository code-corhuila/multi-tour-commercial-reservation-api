package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// HU-COM-006 T-01 (plan D-04): the public paths are listed one by one and admit GET only.
class PublicPathsTest {
    private static final String BASE = "/api/v1/commercial-reservation/public/tenants/agencia-ficticia";
    private static final String ID = "5a8e2f14-91c3-4b7d-86e0-3d4c7a9b1e62";

    @Test
    void aPathUnderPublicThatIsNotListedIsRefused() {
        for (String path : new String[] {BASE, BASE + "/", BASE + "/tours/" + ID + "/departures", BASE + "/other",
                BASE + "/tours/a/b", BASE + "/associated-establishments/" + ID + "/image/x",
                "/api/v1/commercial-reservation/public", "/api/v1/commercial-reservation/public/tenants",
                "/api/v1/commercial-reservation/public/tenants//tours", BASE + "/tours/../x"}) {
            assertThat(PublicPaths.allows("GET", path)).as(path).isFalse();
            assertThat(PublicPaths.isPublicArea(path)).as(path).isTrue();
        }
    }

    @Test
    void theFiveListedRoutesAreOpenToGetOnly() {
        for (String path : new String[] {BASE + "/tours", BASE + "/tours/" + ID, BASE + "/associated-establishments",
                BASE + "/associated-establishments/" + ID, BASE + "/associated-establishments/" + ID + "/image"}) {
            assertThat(PublicPaths.allows("GET", path)).as(path).isTrue();
            for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}) {
                assertThat(PublicPaths.allows(method, path)).as(method + " " + path).isFalse();
            }
        }
    }

    @Test
    void thePathsOutsideThePublicAreaAreNotPublic() {
        for (String path : new String[] {"/health", "/api/v1/commercial-reservation/catalog/services",
                "/api/v1/commercial-reservation/associated-establishments/" + ID,
                "/api/v1/commercial-reservation/internal/tenants/" + ID + "/events"}) {
            assertThat(PublicPaths.isPublicArea(path)).as(path).isFalse();
            assertThat(PublicPaths.allows("GET", path)).as(path).isFalse();
        }
    }
}
