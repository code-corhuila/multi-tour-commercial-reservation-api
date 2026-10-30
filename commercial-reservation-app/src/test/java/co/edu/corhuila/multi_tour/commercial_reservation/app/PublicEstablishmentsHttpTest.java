package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// HU-COM-006 T-05 / T-06 (establishments half): the public list and detail of the active associations, and the image
// route brought under the same rules (AC-06.17). The establishments are planted through the authenticated routes.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
class PublicEstablishmentsHttpTest {
    static final String BASE = "/api/v1/commercial-reservation/public/tenants/";
    static final String IDENTIFIER = "agencia-ficticia", OTHER = "agencia-ajena", INACTIVE = "agencia-inactiva";
    static final String TRACE = CatalogHttpTest.TRACE;
    final UUID foreign = UUID.randomUUID(), own = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired TenantDirectoryHttpFixture.MemoryDirectory directory;
    @Autowired CatalogHttpFixture.MemoryKeys keys;
    String hotel, restaurant, retired, foreignHotel;

    @BeforeEach void seed() throws Exception {
        keys.keys.clear();
        directory.tenants.clear();
        directory.tenants.put(IDENTIFIER, new TenantRef(own, true));
        directory.tenants.put(OTHER, new TenantRef(foreign, true));
        directory.tenants.put(INACTIVE, new TenantRef(new UUID(0, 777), false));
        hotel = plant(own.toString(), "HOTEL", "Hotel Ficticio", "contacto@example.invalid");
        restaurant = plant(own.toString(), "RESTAURANT", "Restaurante Ficticio", "Tel 3000000000");
        retired = plant(own.toString(), "HOTEL", "Hotel Retirado", "ya no");
        foreignHotel = plant(foreign.toString(), "HOTEL", "Hotel Ajeno", "otro");
        mvc.perform(post("/api/v1/commercial-reservation/associated-establishments/" + retired + "/inactivate")
                .header("Authorization", "Bearer " + CatalogHttpTest.token(own.toString(), "TENANT_ADMINISTRATOR"))).andReturn();
    }

    String plant(String tenant, String type, String name, String information) throws Exception {
        var created = mvc.perform(multipart("/api/v1/commercial-reservation/associated-establishments")
                .file(new MockMultipartFile("image", "image.png", "image/png", EstablishmentHttpTest.PNG))
                .param("establishmentType", type).param("commercialName", name).param("commercialInformation", information)
                .header("Idempotency-Key", "public-offer-key-" + UUID.randomUUID())
                .header("Authorization", "Bearer " + CatalogHttpTest.token(tenant, "TENANT_ADMINISTRATOR"))).andReturn();
        return CatalogHttpTest.json(created).get("associationId").asText();
    }

    MvcResult call(String path, int code) throws Exception {
        var result = mvc.perform(get(BASE + path).header("X-Correlation-Id", TRACE)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(code);
        return result;
    }

    static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test void thePublicRoutesAnswerWithoutAToken() throws Exception {
        call(IDENTIFIER + "/associated-establishments", 200);
        call(IDENTIFIER + "/associated-establishments/" + hotel, 200);
        var result = mvc.perform(get(BASE + IDENTIFIER + "/associated-establishments").header("Authorization", "Bearer not-a-token")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test void onlyActiveEstablishmentsOfTheTenantAreListedWithOnlyTheAllowedFields() throws Exception {
        var page = CatalogHttpTest.json(call(IDENTIFIER + "/associated-establishments", 200));
        assertThat(page.get("data")).hasSize(2);
        assertThat(page.at("/meta/total").asInt()).isEqualTo(2);
        for (JsonNode item : page.get("data")) {
            assertThat(names(item)).containsExactlyInAnyOrder("associationId", "establishmentType", "commercialName",
                    "commercialInformation", "imageUrl");
        }
        assertThat(page.toString()).doesNotContain(retired).doesNotContain(foreignHotel).doesNotContain("Hotel Ajeno");
        var detail = CatalogHttpTest.json(call(IDENTIFIER + "/associated-establishments/" + hotel, 200));
        assertThat(detail.get("commercialInformation").asText()).isEqualTo("contacto@example.invalid");
        assertThat(detail.get("establishmentType").asText()).isEqualTo("HOTEL");
    }

    @Test void theImageAddressPointsToTheExistingPublicRouteWithTheIdentifierOfTheVisitor() throws Exception {
        var detail = CatalogHttpTest.json(call(IDENTIFIER + "/associated-establishments/" + hotel, 200));
        String address = detail.get("imageUrl").asText();
        assertThat(address).isEqualTo(BASE + IDENTIFIER + "/associated-establishments/" + hotel + "/image");
        var image = mvc.perform(get(address)).andReturn().getResponse();
        assertThat(image.getStatus()).isEqualTo(200);
        assertThat(image.getContentAsByteArray()).isEqualTo(EstablishmentHttpTest.PNG);
    }

    @Test void theTypeIsTheOnlyFilterAndThereIsNoTextSearch() throws Exception {
        var hotels = CatalogHttpTest.json(call(IDENTIFIER + "/associated-establishments?establishmentType=HOTEL", 200));
        assertThat(hotels.get("data")).hasSize(1);
        assertThat(hotels.get("data").get(0).get("associationId").asText()).isEqualTo(hotel);
        for (String query : new String[] {"?establishmentType=OTHER", "?search=hotel", "?q=hotel", "?limit=0", "?limit=101", "?page=0"}) {
            var result = call(IDENTIFIER + "/associated-establishments" + query, 400);
            assertThat(CatalogHttpTest.json(result).get("error").asText()).as(query).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Test void listsArePaginatedAndATenantWithNothingGetsAnEmptyPage() throws Exception {
        var first = CatalogHttpTest.json(call(IDENTIFIER + "/associated-establishments?page=1&limit=1", 200));
        assertThat(first.get("data")).hasSize(1);
        assertThat(first.at("/meta/total").asInt()).isEqualTo(2);
        assertThat(first.at("/meta/totalPages").asInt()).isEqualTo(2);
        directory.tenants.put("agencia-vacia", new TenantRef(new UUID(0, 555), true));
        var empty = CatalogHttpTest.json(call("agencia-vacia/associated-establishments", 200));
        assertThat(empty.get("data")).isEmpty();
        assertThat(empty.at("/meta/total").asInt()).isZero();
    }

    @Test void theAnswersCarryAShortPublicCachePolicyAndErrorsNone() throws Exception {
        assertThat(call(IDENTIFIER + "/associated-establishments", 200).getResponse().getHeader("Cache-Control"))
                .isEqualTo("public, max-age=60");
        assertThat(call(IDENTIFIER + "/associated-establishments/" + hotel, 200).getResponse().getHeader("Cache-Control"))
                .isEqualTo("public, max-age=60");
        assertThat(call(IDENTIFIER + "/associated-establishments/" + retired, 404).getResponse().getHeader("Cache-Control")).isNull();
    }

    @Test void anInactiveForeignOrUnknownEstablishmentAndTenantAnswerTheSameNotFound() throws Exception {
        String expected = call(IDENTIFIER + "/associated-establishments/" + UUID.randomUUID(), 404).getResponse().getContentAsString();
        assertThat(CatalogHttpTest.json(call(IDENTIFIER + "/associated-establishments/" + retired, 404)).get("error").asText())
                .isEqualTo("NOT_FOUND");
        for (String path : new String[] {IDENTIFIER + "/associated-establishments/" + retired,
                IDENTIFIER + "/associated-establishments/" + foreignHotel, INACTIVE + "/associated-establishments/" + hotel,
                "agencia-desconocida/associated-establishments/" + hotel, INACTIVE + "/associated-establishments",
                "agencia-desconocida/associated-establishments"}) {
            assertThat(call(path, 404).getResponse().getContentAsString()).as(path).isEqualTo(expected);
        }
        call("Not_Valid/associated-establishments", 400);
        call(IDENTIFIER + "/associated-establishments/not-a-uuid", 400);
    }

    @Test void theImageOfAnInactiveOrForeignEstablishmentIsNotFound() throws Exception {
        call(IDENTIFIER + "/associated-establishments/" + retired + "/image", 404);
        call(IDENTIFIER + "/associated-establishments/" + foreignHotel + "/image", 404);
        call(OTHER + "/associated-establishments/" + hotel + "/image", 404);
        call(IDENTIFIER + "/associated-establishments/" + hotel + "/image", 200);
    }
}
