package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcRequest;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.springframework.test.web.servlet.MvcResult;

final class CatalogContract {
    static final Set<String> PENDING = Set.of("listCatalogServices", "updateCatalogService",
            "inactivateCatalogService", "reactivateCatalogService");
    private static final String SPEC = Path.of("../openapi/openapi.yaml").toAbsolutePath().toUri().toString();
    private static final OpenAPI API = new OpenAPIV3Parser().read(SPEC);
    private static final OpenApiInteractionValidator CATALOG = OpenApiInteractionValidator
            .createForSpecificationUrl(SPEC).withBasePathOverride("/api/v1").build();
    private static final OpenApiInteractionValidator HEALTH = OpenApiInteractionValidator
            .createForSpecificationUrl(SPEC).withBasePathOverride("").build();
    private final Set<String> exercised = new HashSet<>();

    void validate(MvcResult result, boolean invalidRequest) {
        var request = MockMvcRequest.of(result.getRequest());
        var validator = request.getPath().equals("/health") ? HEALTH : CATALOG;
        var requestReport = validator.validateRequest(request);
        assertThat(requestReport.hasErrors()).as("request contract: %s", requestReport).isEqualTo(invalidRequest);
        var responseReport = validator.validateResponse(request.getPath(), request.getMethod(),
                MockMvcResponse.of(result.getResponse()));
        assertThat(responseReport.hasErrors()).as("response contract: %s", responseReport).isFalse();
        String path = request.getPath().replaceFirst("^/api/v1", "")
                .replaceFirst("^(/catalog/services)/[^/]+$", "$1/{serviceId}");
        var operation = API.getPaths().get(path).readOperationsMap()
                .get(PathItem.HttpMethod.valueOf(request.getMethod().name()));
        assertThat(operation).as("declared HTTP operation").isNotNull();
        exercised.add(operation.getOperationId());
    }

    void assertCoverage() {
        Set<String> declared = new HashSet<>();
        API.getPaths().values().forEach(path -> path.readOperations()
                .forEach(operation -> declared.add(operation.getOperationId())));
        assertThat(exercised).doesNotContainAnyElementsOf(PENDING);
        Set<String> covered = new HashSet<>(exercised);
        covered.addAll(PENDING);
        assertThat(covered).containsExactlyInAnyOrderElementsOf(declared);
    }
}
