package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

// api spec 021 T-04 (RN-TSC-07): the public image route resolves the identifier through the tenant directory, and an
// unknown or inactive tenant answers the shared 404. The directory here is a fake; the JDBC one has its own test.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, PublicTenantHttpTest.Directory.class})
class PublicTenantHttpTest {
    static final String PUBLIC = "/api/v1/commercial-reservation/public/tenants/";
    static final UUID TENANT = UUID.fromString(TestJwtKeys.TENANT);

    @TestConfiguration
    static class Directory {
        @Bean @Primary MemoryDirectory memoryDirectory() { return new MemoryDirectory(); }
    }

    static final class MemoryDirectory implements TenantDirectory {
        final Map<String, TenantRef> tenants = new HashMap<>();
        public Optional<TenantRef> resolve(String identifier) { return Optional.ofNullable(tenants.get(identifier)); }
    }

    @Autowired MockMvc mvc;
    @Autowired MemoryDirectory directory;
    String association;

    @BeforeEach void plant() throws Exception {
        directory.tenants.clear();
        directory.tenants.put("agencia-ficticia", new TenantRef(TENANT, true));
        directory.tenants.put("agencia-inactiva", new TenantRef(TENANT, false));
        var created = mvc.perform(multipart("/api/v1/commercial-reservation/associated-establishments")
                .file(new MockMultipartFile("image", "image.png", "image/png", EstablishmentHttpTest.PNG))
                .param("establishmentType", "HOTEL").param("commercialName", "Hotel example")
                .param("commercialInformation", "Promotional contact information")
                .header("Idempotency-Key", "tenant-directory-key-" + UUID.randomUUID())
                .header("Authorization", "Bearer " + CatalogHttpTest.token(TestJwtKeys.TENANT, "TENANT_ADMINISTRATOR"))).andReturn();
        association = CatalogHttpTest.json(created).get("associationId").asText();
    }

    int image(String identifier) throws Exception {
        return mvc.perform(get(PUBLIC + identifier + "/associated-establishments/" + association + "/image"))
                .andReturn().getResponse().getStatus();
    }

    @Test void publicRoutesAnswer404ForAnUnknownOrInactiveTenant() throws Exception {
        assertThat(image("agencia-ficticia")).isEqualTo(200);
        assertThat(image("agencia-inactiva")).isEqualTo(404);
        assertThat(image("agencia-desconocida")).isEqualTo(404);
        assertThat(image("Not_Valid")).isEqualTo(400);
    }
}
