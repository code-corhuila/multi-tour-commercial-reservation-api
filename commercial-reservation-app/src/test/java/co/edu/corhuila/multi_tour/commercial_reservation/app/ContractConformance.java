package co.edu.corhuila.multi_tour.commercial_reservation.app;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcRequest;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * ADR-004 D7: every response of the HTTP tests is validated against the published contract in
 * {@code openapi/openapi.yaml}, and the operations the tests exercised are collected for {@link ContractCoverageTest}.
 *
 * <p>Registered for every Spring test context through {@code META-INF/spring/...AutoConfiguration.imports}, so no test
 * has to call it. What is validated: the response of every exchange with a route of the contract, and a 2xx or 3xx
 * response of a route that the contract does not declare is a failure (the contract and the code drifted apart).
 * What is not validated, on purpose: {@code /test-support} (probes that exist only in the tests) and the exchanges that
 * hit a route or a method the contract does not declare and answer 4xx (the intentional negative tests, such as
 * the delete and update probes of the catalog). Requests are not validated here: the negative tests send invalid
 * requests on purpose, and the catalog and establishment tests keep their own request gate ({@link CatalogContract}).
 */
@AutoConfiguration
public class ContractConformance {

    static final String BASE = "/api/v1/commercial-reservation";

    static {
        // The validator logs the exchanges it checks; the tests that look for personal data in the output must not see them.
        if (org.slf4j.LoggerFactory.getLogger("com.atlassian.oai") instanceof ch.qos.logback.classic.Logger logger) {
            logger.setLevel(ch.qos.logback.classic.Level.ERROR);
        }
    }

    private static final String SPEC = Path.of("../openapi/openapi.yaml").toAbsolutePath().toUri().toString();
    private static final OpenAPI API = new OpenAPIV3Parser().read(SPEC);
    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
            .createForSpecificationUrl(SPEC).withBasePathOverride(BASE).build();
    private static final OpenApiInteractionValidator HEALTH = OpenApiInteractionValidator
            .createForSpecificationUrl(SPEC).withBasePathOverride("").build();

    /** The operations ("METHOD /template") that the tests of this JVM exercised, for the coverage test. */
    static final Set<String> EXERCISED = ConcurrentHashMap.newKeySet();

    /**
     * Responses that still differ from the contract, as "METHOD /template status", each with the reason and the way out.
     * It must end empty: an entry is removed when the code or the contract is reconciled.
     */
    static final Set<String> RECONCILING = Set.of(
            "PUT /payment-parameters 200",
            "PUT /payment-parameters 422",
            "GET /payment-parameters 200",
            "POST /internal/tenants/{tenantId}/payment-proofs/{paymentId}/validated-payment 200",
            "POST /financial-decisions/{financialDecisionId}/refund-decision 200",
            "POST /payment-proofs/{paymentId}/reject 200",
            "GET /payment-proofs 200",
            "GET /payment-proofs/{paymentId} 200",
            "GET /internal/tenants/{tenantId}/payment-proofs 200",
            "GET /credit-balances/{creditBalanceId} 200",
            "GET /public/tenants/{identifier}/tours/{serviceId} 200",
            "POST /internal/outbox/{eventId}/published 200"
    );

    private record Route(String method, String template, Pattern pattern, int parameters) {}

    private static final List<Route> ROUTES = routes();

    private static List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        API.getPaths().forEach((template, item) -> item.readOperationsMap().forEach((method, operation) -> routes.add(
                new Route(method.name(), template, Pattern.compile("^" + template.replaceAll("[{][^}]+[}]", "[^/]+") + "$"),
                        template.split("[{]", -1).length - 1))));
        routes.sort(Comparator.comparingInt(Route::parameters));
        return routes;
    }

    static Set<String> declared() {
        Set<String> declared = new java.util.TreeSet<>();
        ROUTES.forEach(route -> declared.add(route.method() + " " + route.template()));
        return declared;
    }

    static String operationOf(String method, String relativePath) {
        return ROUTES.stream().filter(route -> route.method().equals(method) && route.pattern().matcher(relativePath).matches())
                .map(route -> route.method() + " " + route.template()).findFirst().orElse(null);
    }

    @Bean
    MockMvcBuilderCustomizer contractConformance() {
        return builder -> builder.alwaysDo(result -> {
            var request = MockMvcRequest.of(result.getRequest());
            String path = request.getPath();
            int status = result.getResponse().getStatus();
            if (path.startsWith("/test-support")) {
                return;
            }
            String relative = path.startsWith(BASE) ? path.substring(BASE.length()) : path;
            String operation = operationOf(request.getMethod().name(), relative);
            if (operation == null) {
                if (status < 400) {
                    fail(path, "the route answers " + status + " but the contract does not declare it");
                }
                return;
            }
            if (status != 404 && status != 405) {
                EXERCISED.add(operation);
            }
            var validator = request.getPath().equals("/health") ? HEALTH : VALIDATOR;
            var report = validator.validateResponse(path, request.getMethod(), MockMvcResponse.of(result.getResponse()));
            var messages = report.getMessages().stream().filter(m -> m.getKey().startsWith("validation.response."))
                    .map(m -> m.getKey() + ": " + m.getMessage().replace('\n', ' ')).toList();
            if (!messages.isEmpty() && !RECONCILING.contains(operation + " " + status)) {
                String line = operation + " " + status + " -> " + messages;
                if (System.getProperty("contract.report") != null) {
                    record(line);
                } else {
                    fail(path, line);
                }
            }
        });
    }

    private static void fail(String path, String reason) {
        throw new AssertionError("response contract (" + path + "): " + reason);
    }

    private static void record(String line) {
        try {
            synchronized (ContractConformance.class) {
                Files.writeString(Path.of(System.getProperty("contract.report")), line + "\n", StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
