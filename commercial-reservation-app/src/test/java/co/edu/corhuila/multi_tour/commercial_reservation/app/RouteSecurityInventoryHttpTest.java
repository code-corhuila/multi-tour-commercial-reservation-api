package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// K4 (rubric c3-release): the permissions are checked on the server, by an inventory of the routes and not by reading the controllers. Every route
// that writes (POST, PUT, PATCH, DELETE) is asked without a token and answers 401, and with the token of a role that may not write it answers
// 403: the read-only role Analyst for a route of the area, and the role of a Tenant Administrator for a route of the platform
// (/internal/**), which only accepts SERVICE. A route added later is covered without touching this test; the only public write is listed here.
class RouteSecurityInventoryHttpTest extends ReservationHttpBase {
    // path (as mapped) -> why it needs no token. Empty today: no route of the api writes without a token.
    private static final Map<String, String> PUBLIC_WRITES = Map.of();

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    private record Route(HttpMethod method, String pattern, boolean multipart) {
        boolean internal() { return pattern.contains("/internal/"); }

        String path() { return pattern.replaceAll("\\{[^/}]+}", new UUID(0, 42).toString()); }

        @Override public String toString() { return method + " " + pattern; }
    }

    private Set<Route> writingRoutes() {
        Set<Route> found = new TreeSet<>(java.util.Comparator.comparing(Route::toString));
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) continue;
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith(BASE) || pattern.contains("/test-support")) continue;
                for (var method : info.getMethodsCondition().getMethods()) {
                    HttpMethod http = HttpMethod.valueOf(method.name());
                    if (http.equals(HttpMethod.POST) || http.equals(HttpMethod.PUT) || http.equals(HttpMethod.PATCH) || http.equals(HttpMethod.DELETE)) {
                        found.add(new Route(http, pattern, info.getConsumesCondition().getConsumableMediaTypes().stream().anyMatch(type -> type.isCompatibleWith(MediaType.MULTIPART_FORM_DATA))));
                    }
                }
            }
        }
        return found;
    }

    private MockHttpServletRequestBuilder call(Route route) {
        return request(route.method(), route.path()).contentType(route.multipart() ? MediaType.MULTIPART_FORM_DATA : MediaType.APPLICATION_JSON).content(route.multipart() ? "" : "{}").header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Correlation-Id", TRACE);
    }

    @Test void theInventoryFindsTheWritingRoutesOfTheApi() {
        assertThat(writingRoutes()).as("writing routes mapped under " + BASE).hasSizeGreaterThan(40);
    }

    @Test void everyWritingRouteRefusesARequestWithoutAToken() throws Exception {
        Map<String, Integer> wrong = new TreeMap<>();
        for (Route route : writingRoutes()) {
            if (PUBLIC_WRITES.containsKey(route.pattern())) continue;
            int code = mvc.perform(call(route)).andReturn().getResponse().getStatus();
            if (code != 401) wrong.put(route.toString(), code);
        }

        assertThat(wrong).as("writing routes that do not answer 401 without a token").isEmpty();
    }

    @Test void everyWritingRouteRefusesTheTokenOfARoleThatMayNotWriteIt() throws Exception {
        Map<String, Integer> wrong = new TreeMap<>();
        for (Route route : writingRoutes()) {
            if (PUBLIC_WRITES.containsKey(route.pattern())) continue;
            String role = route.internal() ? ADMIN : ANALYST;
            int code = mvc.perform(withToken(call(route), role)).andReturn().getResponse().getStatus();
            if (code != 403) wrong.put(route + " with " + role, code);
        }

        assertThat(wrong).as("writing routes that do not answer 403 to a role that may not write them").isEmpty();
    }
}
