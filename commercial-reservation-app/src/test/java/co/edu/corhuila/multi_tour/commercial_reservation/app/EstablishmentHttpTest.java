package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EstablishmentHttpTest {
    static final String BASE = "/api/v1/associated-establishments";
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    static final String SPEC = Path.of("../openapi/openapi.yaml").toAbsolutePath().toUri().toString();
    final OpenApiInteractionValidator validator = OpenApiInteractionValidator
            .createForSpecificationUrl(SPEC).withBasePathOverride("/api/v1").build();
    final Set<String> exercised = new HashSet<>();
    @Autowired MockMvc mvc;
    String tenant;

    @BeforeEach void tenant() { tenant = UUID.randomUUID().toString(); }

    @Test void registersAndPublishesAnEstablishmentWithItsImage() throws Exception {
        var result = exchange(upload(PNG, "anything.svg", "text/plain"), 201, "TENANT_ADMINISTRATOR");
        var body = json(result);
        assertThat(UUID.fromString(body.get("associationId").asText()).toString())
                .isEqualTo(body.get("associationId").asText());
        assertThat(body.get("associationStatus").asText()).isEqualTo("ACTIVE");
        assertThat(body.get("commercialName").asText()).isEqualTo("Hotel example");
        assertThat(body.get("commercialInformation").asText()).isEqualTo("Promotional contact information");
        assertThat(body.get("imageReference").asText()).isNotBlank();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(path(body));
        assertThat(json(exchange(get(path(body)), 200, "TENANT_ADMINISTRATOR"))).isEqualTo(body);
        var image = exchange(get(imagePath(tenant, body)), 200, null);
        assertThat(image.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(image.getResponse().getContentType()).isEqualTo("image/png");
    }

    @ParameterizedTest @ValueSource(strings = {"oversize", "svg", "gif", "junk"})
    void refusesAnOversizedOrWrongTypeImageAndCreatesNoAssociation(String failure) throws Exception {
        byte[] bytes = switch (failure) {
            case "oversize" -> Arrays.copyOf(PNG, 819201);
            case "svg" -> "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes();
            case "gif" -> "GIF89a".getBytes();
            default -> "not an image".getBytes();
        };
        var refused = exchange(upload(bytes, "renamed.png", "image/png"),
                failure.equals("oversize") ? 413 : 415, "TENANT_ADMINISTRATOR");
        assertField(refused, "image");
        assertThat(json(exchange(get(BASE), 200, "TENANT_ADMINISTRATOR")).at("/meta/total").intValue()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"png", "jpeg", "webp"})
    void imageSignatureOverridesTheFilenameAndContentType(String type) throws Exception {
        byte[] bytes = switch (type) {
            case "jpeg" -> new byte[] {(byte) 255, (byte) 216, (byte) 255, (byte) 217};
            case "webp" -> new byte[] {82, 73, 70, 70, 4, 0, 0, 0, 87, 69, 66, 80};
            default -> Arrays.copyOf(PNG, 819200);
        };
        var item = json(exchange(upload(bytes, "wrong.svg", "application/octet-stream"), 201, "TENANT_ADMINISTRATOR"));
        var image = exchange(get(imagePath(tenant, item)), 200, null);
        assertThat(image.getResponse().getContentAsByteArray()).isEqualTo(bytes);
        assertThat(image.getResponse().getContentType()).isEqualTo("image/" + type);
    }

    @Test void sameIdempotencyKeyAnswers200WithTheSameAssociation() throws Exception {
        var first = json(exchange(upload(PNG, "first.png", "image/png"), 201, "TENANT_ADMINISTRATOR"));
        var retry = json(exchange(upload(PNG, "retry.png", "image/png"), 200, "TENANT_ADMINISTRATOR"));
        assertThat(retry).isEqualTo(first);
        assertThat(json(exchange(get(BASE), 200, "TENANT_ADMINISTRATOR")).at("/meta/total").intValue()).isEqualTo(1);
        tenant = UUID.randomUUID().toString();
        var other = json(exchange(upload(PNG, "first.png", "image/png"), 201, "TENANT_ADMINISTRATOR"));
        assertThat(other.get("associationId")).isNotEqualTo(first.get("associationId"));
    }

    @Test void publicImageRouteServesWithoutTokenAndWithCacheHeader() throws Exception {
        var item = seed();
        exchange(post(path(item) + "/inactivate"), 200, "TENANT_ADMINISTRATOR");
        var image = exchange(get(imagePath(tenant, item)), 200, null);
        assertThat(image.getResponse().getHeader("Cache-Control")).contains("public", "max-age=").doesNotContain("no-store");
        assertThat(image.getResponse().getContentAsByteArray()).isEqualTo(PNG);
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "inactivate", "reactivate", "image"})
    void otherTenantAnswersAsNonexistent(String operation) throws Exception {
        var item = seed();
        String owner = tenant;
        tenant = UUID.randomUUID().toString();
        String id = item.get("associationId").asText();
        var other = exchange(operation(operation, id), 404, operation.equals("image") ? null : "TENANT_ADMINISTRATOR");
        var missing = exchange(operation(operation, UUID.randomUUID().toString()), 404,
                operation.equals("image") ? null : "TENANT_ADMINISTRATOR");
        assertThat(json(other)).isEqualTo(json(missing));
        tenant = owner;
        assertThat(json(exchange(get(path(item)), 200, "TENANT_ADMINISTRATOR"))).isEqualTo(item);
    }

    @ParameterizedTest @ValueSource(strings = {"register", "inactivate", "reactivate"})
    void collaboratorIsDeniedOnRegisterInactivateAndReactivate(String operation) throws Exception {
        var item = seed();
        exchange(operation.equals("register") ? upload(PNG, "image.png", "image/png")
                : post(path(item) + "/" + operation), 403, "OPERATIONAL_COLLABORATOR");
        assertThat(json(exchange(get(path(item)), 200, "TENANT_ADMINISTRATOR"))).isEqualTo(item);
        assertThat(json(exchange(get(BASE), 200, "TENANT_ADMINISTRATOR")).at("/meta/total").intValue()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"ACCOUNTANT", "OPERATIONAL_COLLABORATOR"})
    void accountantAndCollaboratorCannotRead(String role) throws Exception {
        var item = seed();
        exchange(get(BASE), 403, role);
        exchange(get(path(item)), 403, role);
    }

    @ParameterizedTest @ValueSource(strings = {"MANAGER", "ANALYST"})
    void managerAndAnalystReadWithoutWriting(String role) throws Exception {
        var item = seed();
        exchange(get(BASE), 200, role);
        assertThat(json(exchange(get(path(item)), 200, role))).isEqualTo(item);
        exchange(upload(PNG, "image.png", "image/png"), 403, role);
        exchange(post(path(item) + "/inactivate"), 403, role);
        exchange(post(path(item) + "/reactivate"), 403, role);
        assertThat(json(exchange(get(path(item)), 200, "TENANT_ADMINISTRATOR"))).isEqualTo(item);
    }

    @ParameterizedTest @ValueSource(strings = {"DELETE", "PATCH", "PUT"})
    void noDeleteOperationExistsInRoutesPortsOrContract(String method) throws Exception {
        var item = seed();
        var result = mvc.perform(request(HttpMethod.valueOf(method), path(item))
                .header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, "TENANT_ADMINISTRATOR"))
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().isNotFound().match(result);
        CatalogHttpTest.assertError(result, "NOT_FOUND", "resource not found");
        var api = new OpenAPIV3Parser().read(SPEC);
        api.getPaths().entrySet().stream().filter(entry -> entry.getKey().contains("associated-establishments"))
                .forEach(entry -> assertThat(entry.getValue().readOperationsMap())
                        .doesNotContainKeys(PathItem.HttpMethod.DELETE, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.PUT));
        assertThat(json(exchange(get(path(item)), 200, "TENANT_ADMINISTRATOR"))).isEqualTo(item);
    }

    @ParameterizedTest @ValueSource(strings = {"list", "get", "register", "inactivate", "reactivate", "imagePost", "imageSuffix", "catalog"})
    void noOtherRouteIsPublic(String operation) throws Exception {
        var item = seed();
        MockHttpServletRequestBuilder request = switch (operation) {
            case "list" -> get(BASE);
            case "get" -> get(path(item));
            case "register" -> upload(PNG, "image.png", "image/png");
            case "imagePost" -> post(imagePath(tenant, item));
            case "imageSuffix" -> get(imagePath(tenant, item) + "/extra");
            case "catalog" -> get(CatalogHttpTest.BASE);
            default -> post(path(item) + "/" + operation);
        };
        var result = mvc.perform(request.header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().isUnauthorized().match(result);
        CatalogHttpTest.assertError(result, "UNAUTHORIZED", "authentication is required");
    }

    @ParameterizedTest @ValueSource(strings = {"establishmentType", "commercialName", "commercialInformation", "image", "Idempotency-Key", "invalidType", "longName"})
    void invalidRegistrationNamesTheField(String field) throws Exception {
        var request = multipart(BASE);
        if (!field.equals("image")) request.file(new MockMultipartFile("image", "image.png", "image/png", PNG));
        if (!field.equals("establishmentType")) request.param("establishmentType", field.equals("invalidType") ? "HOSTEL" : "HOTEL");
        if (!field.equals("commercialName")) request.param("commercialName", field.equals("longName") ? "x".repeat(256) : "Hotel");
        if (!field.equals("commercialInformation")) request.param("commercialInformation", "Contact");
        if (!field.equals("Idempotency-Key")) request.header("Idempotency-Key", "test-establishment-key");
        assertField(exchange(request, 400, "TENANT_ADMINISTRATOR"),
                field.equals("invalidType") ? "establishmentType" : field.equals("longName") ? "commercialName" : field);
    }

    @ParameterizedTest @ValueSource(strings = {"rooms", "dishes", "menus", "seats", "rates", "availability", "tenantId"})
    void registrationAcceptsNoInventoryOrTenantOverride(String field) throws Exception {
        assertField(exchange(upload(PNG, "image.png", "image/png").param(field, "forbidden"), 400,
                "TENANT_ADMINISTRATOR"), field);
    }

    @Test void transitionsAreIdempotentAndListingRetainsHistory() throws Exception {
        var item = seed();
        var inactive = json(exchange(post(path(item) + "/inactivate"), 200, "TENANT_ADMINISTRATOR"));
        assertThat(inactive.get("associationStatus").asText()).isEqualTo("INACTIVE");
        assertThat(json(exchange(get(path(item)), 200, "TENANT_ADMINISTRATOR"))).isEqualTo(inactive);
        assertThat(json(exchange(post(path(item) + "/inactivate"), 200, "TENANT_ADMINISTRATOR"))).isEqualTo(inactive);
        assertThat(json(exchange(get(BASE).param("associationStatus", "ACTIVE"), 200,
                "TENANT_ADMINISTRATOR")).get("data")).isEmpty();
        assertThat(json(exchange(get(BASE).param("associationStatus", "INACTIVE").param("establishmentType", "HOTEL"),
                200, "TENANT_ADMINISTRATOR")).at("/meta/total").intValue()).isEqualTo(1);
        var active = json(exchange(post(path(item) + "/reactivate"), 200, "TENANT_ADMINISTRATOR"));
        assertThat(active).isEqualTo(item);
        assertThat(json(exchange(post(path(item) + "/reactivate"), 200, "TENANT_ADMINISTRATOR"))).isEqualTo(active);
    }

    @ParameterizedTest @CsvSource({"page,0", "limit,0", "limit,101", "limit,invalid", "associationStatus,OTHER", "establishmentType,OTHER", "unknown,value"})
    void invalidListingNamesTheField(String field, String value) throws Exception {
        assertField(exchange(get(BASE).param(field, value), 400, "TENANT_ADMINISTRATOR"), field);
    }

    @Test void listingIsPagedAndNewestFirst() throws Exception {
        var ids = new ArrayList<String>();
        for (int i = 0; i < 23; i++) ids.add(json(exchange(upload(PNG, "image.png", "image/png")
                .header("Idempotency-Key", "page-key-" + i), 201, "TENANT_ADMINISTRATOR")).get("associationId").asText());
        // Fixed timestamps require the identifier tie-breaker to preserve deterministic pagination.
        ids.sort(Comparator.reverseOrder());
        var first = json(exchange(get(BASE), 200, "TENANT_ADMINISTRATOR"));
        assertThat(first.at("/meta/limit").intValue()).isEqualTo(20);
        assertThat(first.get("data").size()).isEqualTo(20);
        var second = json(exchange(get(BASE).param("page", "2").param("limit", "3"), 200, "TENANT_ADMINISTRATOR"));
        assertThat(second.at("/meta/page").intValue()).isEqualTo(2);
        assertThat(second.at("/meta/limit").intValue()).isEqualTo(3);
        assertThat(second.at("/meta/total").intValue()).isEqualTo(23);
        assertThat(second.at("/meta/totalPages").intValue()).isEqualTo(8);
        for (int i = 0; i < 3; i++) assertThat(second.get("data").get(i).get("associationId").asText()).isEqualTo(ids.get(3 + i));
    }

    @Test @Order(Integer.MAX_VALUE) void contractOperationsAreExercised() {
        var declared = new HashSet<String>();
        new OpenAPIV3Parser().read(SPEC).getPaths().entrySet().stream()
                .filter(entry -> entry.getKey().contains("associated-establishments"))
                .forEach(entry -> entry.getValue().readOperations().forEach(operation -> declared.add(operation.getOperationId())));
        assertThat(exercised).containsExactlyInAnyOrderElementsOf(declared);
        assertThat(CatalogContract.PENDING).isEmpty();
    }

    private JsonNode seed() throws Exception { return json(exchange(upload(PNG, "image.png", "image/png"), 201, "TENANT_ADMINISTRATOR")); }
    private MockMultipartHttpServletRequestBuilder upload(byte[] bytes, String filename, String type) {
        var request = multipart(BASE).file(new MockMultipartFile("image", filename, type, bytes));
        request.param("establishmentType", "HOTEL").param("commercialName", "Hotel example")
                .param("commercialInformation", "Promotional contact information").header("Idempotency-Key", "test-establishment-key");
        return request;
    }
    private String path(JsonNode item) { return BASE + "/" + item.get("associationId").asText(); }
    private String imagePath(String owner, JsonNode item) { return "/api/v1/public/tenants/" + owner + path(item).substring(7) + "/image"; }
    private MockHttpServletRequestBuilder operation(String operation, String id) {
        return operation.equals("GET") ? get(BASE + "/" + id) : operation.equals("image")
                ? get("/api/v1/public/tenants/" + tenant + "/associated-establishments/" + id + "/image") : post(BASE + "/" + id + "/" + operation);
    }
    private JsonNode json(MvcResult result) throws Exception { return CatalogHttpTest.json(result); }
    private void assertField(MvcResult result, String field) throws Exception {
        var body = json(result);
        assertThat(body.get("error").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo(field);
    }
    private MvcResult exchange(MockHttpServletRequestBuilder request, int code, String role) throws Exception {
        if (role != null) request.header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, role));
        var result = mvc.perform(request.header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        String path = result.getRequest().getRequestURI().substring(7);
        String template = path.replaceFirst("/associated-establishments/[^/]+(?=/|$)", "/associated-establishments/{associationId}")
                .replaceFirst("/public/tenants/[^/]+", "/public/tenants/{identifier}");
        var method = PathItem.HttpMethod.valueOf(result.getRequest().getMethod());
        var response = validator.validateResponse(path, com.atlassian.oai.validator.model.Request.Method.valueOf(method.name()), MockMvcResponse.of(result.getResponse()));
        assertThat(response.hasErrors()).as("response contract: %s", response).isFalse();
        exercised.add(new OpenAPIV3Parser().read(SPEC).getPaths().get(template).readOperationsMap().get(method).getOperationId());
        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isEqualTo(CatalogHttpTest.TRACE);
        if (code >= 400) {
            var body = json(result);
            assertThat(body.get("message").asText()).isNotBlank();
            assertThat(body.get("traceId").asText()).isEqualTo(CatalogHttpTest.TRACE);
            if (code == 403) CatalogHttpTest.assertError(result, "FORBIDDEN", "access is forbidden");
        }
        return result;
    }
}
