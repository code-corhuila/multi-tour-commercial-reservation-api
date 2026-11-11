package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
class EstablishmentTransportTest {
    @LocalServerPort int port;
    @Autowired AssociatedEstablishmentRepository repository;

    @Test void servletUploadLimitReturnsTheCommon413EnvelopeAndWritesNothing() throws Exception {
        UUID tenant = UUID.randomUUID();
        String boundary = "establishment-transport-boundary";
        var bytes = new ByteArrayOutputStream();
        for (String[] field : new String[][] {{"establishmentType", "HOTEL"}, {"commercialName", "Hotel"},
                {"commercialInformation", "Contact"}}) {
            bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field[0]
                    + "\"\r\n\r\n" + field[1] + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"photo.png\""
                + "\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(Arrays.copyOf(EstablishmentHttpTest.PNG, 819201));
        bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + EstablishmentHttpTest.BASE))
                .header("Authorization", "Bearer " + CatalogHttpTest.token(tenant.toString(), "TENANT_ADMINISTRATOR"))
                .header("Idempotency-Key", "transport-test-key")
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(413);
            var body = TestJwtKeys.JSON.readTree(response.body());
            assertThat(body.get("error").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(body.at("/details/0/field").asText()).isEqualTo("image");
            assertThat(body.get("traceId").asText()).isEqualTo(CatalogHttpTest.TRACE);
            assertThat(response.headers().firstValue("X-Correlation-Id")).contains(CatalogHttpTest.TRACE);
            var servletResponse = new org.springframework.mock.web.MockHttpServletResponse();
            servletResponse.setStatus(response.statusCode());
            servletResponse.setContentType(response.headers().firstValue("Content-Type").orElseThrow());
            servletResponse.getWriter().write(response.body());
            var report = com.atlassian.oai.validator.OpenApiInteractionValidator
                    .createForSpecificationUrl(EstablishmentHttpTest.SPEC).withBasePathOverride("/api/v1").build()
                    .validateResponse("/associated-establishments", com.atlassian.oai.validator.model.Request.Method.POST,
                            com.atlassian.oai.validator.mockmvc.MockMvcResponse.of(servletResponse));
            assertThat(report.hasErrors()).as("response contract: %s", report).isFalse();
        }
        assertThat(repository.listByTenant(tenant, java.util.Optional.empty(), java.util.Optional.empty(), 1, 20)
                .total()).isZero();
    }
}
