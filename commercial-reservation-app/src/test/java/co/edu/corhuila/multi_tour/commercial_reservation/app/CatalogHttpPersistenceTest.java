package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = CatalogHttpPersistenceTest.Database.class)
class CatalogHttpPersistenceTest {
    public static class Database implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            new TestJwtKeys().initialize(context);
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "DB_URL=" + System.getenv("TEST_DATABASE_URL"),
                    "DB_USER=" + System.getenv("TEST_DATABASE_USER"),
                    "DB_PASSWORD=" + System.getenv("TEST_DATABASE_PASSWORD"));
        }
    }

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock integrationClock() { return Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC); }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogServiceRepository repository;

    MvcResult exchange(MockHttpServletRequestBuilder request, String tenant, int code, boolean invalid)
            throws Exception {
        var result = mvc.perform(request.header("Authorization", "Bearer " +
                CatalogHttpTest.token(tenant, "TENANT_ADMINISTRATOR"))
                .header("X-Correlation-Id", CatalogHttpTest.TRACE)).andReturn();
        status().is(code).match(result);
        new CatalogContract().validate(result, invalid);
        return result;
    }

    @Test void listingPersistsTotalsPagesStableOrderCombinedFiltersAndTenantIsolation() throws Exception {
        UUID tenant = UUID.randomUUID();
        List<CatalogService> stored = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            var item = CatalogService.restore(UUID.randomUUID(), tenant,
                    i % 2 == 0 ? CatalogServiceType.TOUR : CatalogServiceType.TRANSPORTATION, "Stored item " + i,
                    i % 3 == 0 ? CatalogStatus.INACTIVE : CatalogStatus.ACTIVE,
                    CommercialTerms.of(100L, 50L, 18, false, false),
                    TestJwtKeys.NOW.minusSeconds(1), TestJwtKeys.NOW.minusSeconds(1));
            repository.save(item); stored.add(item);
        }
        repository.save(CatalogService.register(UUID.randomUUID(), UUID.randomUUID(), CatalogServiceType.TOUR,
                "Foreign item", CommercialTerms.of(100L, 50L, 18, false, false), TestJwtKeys.NOW));
        List<String> expected = stored.stream().map(item -> item.serviceId().toString())
                .sorted(Comparator.reverseOrder()).toList();
        List<String> actual = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            var response = CatalogHttpTest.json(exchange(get(CatalogHttpTest.BASE).param("page", "" + page)
                    .param("limit", "20"), tenant.toString(), 200, false));
            assertThat(response.at("/meta/page").intValue()).isEqualTo(page);
            assertThat(response.at("/meta/limit").intValue()).isEqualTo(20);
            assertThat(response.at("/meta/total").longValue()).isEqualTo(45);
            assertThat(response.at("/meta/totalPages").longValue()).isEqualTo(3);
            assertThat(response.get("data").size()).isEqualTo(page == 3 ? 5 : 20);
            actual.addAll(CatalogHttpTest.identifiers(response.get("data")));
        }
        assertThat(actual).containsExactlyElementsOf(expected);
        var beyond = CatalogHttpTest.json(exchange(get(CatalogHttpTest.BASE).param("page", "4"),
                tenant.toString(), 200, false));
        assertThat(beyond.get("data").size()).isZero();
        assertThat(beyond.at("/meta/total").longValue()).isEqualTo(45);
        var filtered = CatalogHttpTest.json(exchange(get(CatalogHttpTest.BASE).param("catalogStatus", "INACTIVE")
                .param("serviceType", "TOUR"), tenant.toString(), 200, false));
        List<String> matching = stored.stream().filter(item -> item.catalogStatus() == CatalogStatus.INACTIVE)
                .filter(item -> item.serviceType() == CatalogServiceType.TOUR)
                .map(item -> item.serviceId().toString()).sorted(Comparator.reverseOrder()).toList();
        assertThat(CatalogHttpTest.identifiers(filtered.get("data"))).containsExactlyElementsOf(matching);
        assertThat(filtered.at("/meta/total").longValue()).isEqualTo(matching.size());
    }

    @Test void invalidTermsRollBackTheNameInTheSameTransaction() throws Exception {
        UUID tenant = UUID.randomUUID();
        var original = CatalogService.register(UUID.randomUUID(), tenant, CatalogServiceType.TOUR, "Original name",
                CommercialTerms.of(100L, 50L, 18, false, false), TestJwtKeys.NOW.minusSeconds(1));
        repository.save(original);
        var result = exchange(patch(CatalogHttpTest.BASE + "/" + original.serviceId())
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(
                        Map.of("commercialName", "Changed name", "baseRateCents", -1))),
                tenant.toString(), 400, true);
        CatalogHttpTest.assertField(result, "baseRateCents");
        var unchanged = repository.findByTenantAndId(tenant, original.serviceId()).orElseThrow();
        assertThat(unchanged.commercialName()).isEqualTo("Original name");
        assertThat(unchanged.terms()).isEqualTo(original.terms());
        assertThat(unchanged.updatedAt()).isEqualTo(original.updatedAt());
        var updated = CatalogHttpTest.json(exchange(patch(CatalogHttpTest.BASE + "/" + original.serviceId())
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(
                        Map.of("commercialName", "Changed name", "baseRateCents", 200))),
                tenant.toString(), 200, false));
        var fetched = CatalogHttpTest.json(exchange(get(CatalogHttpTest.BASE + "/" + original.serviceId()),
                tenant.toString(), 200, false));
        assertThat(updated).isEqualTo(fetched);
        assertThat(updated.get("serviceId").asText()).isEqualTo(original.serviceId().toString());
        assertThat(updated.get("createdAt").asText()).isEqualTo(original.createdAt().toString());
        assertThat(updated.get("updatedAt").asText()).isEqualTo(TestJwtKeys.NOW.toString());
        assertThat(updated.get("commercialName").asText()).isEqualTo("Changed name");
        assertThat(updated.get("baseRateCents").longValue()).isEqualTo(200);
    }

    @Test void repeatedPostRollsBackTheSecondResource() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("commercial_reservation_app");
        String tenant = UUID.randomUUID().toString();
        String token = CatalogHttpTest.token(tenant, "TENANT_ADMINISTRATOR");
        String key = UUID.randomUUID().toString();
        String original = null;
        CatalogContract contract = new CatalogContract();
        for (int attempt = 0; attempt < 2; attempt++) {
            var result = mvc.perform(post(CatalogHttpTest.BASE).header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                    .content(TestJwtKeys.JSON.writeValueAsBytes(CatalogHttpTest.body()))).andReturn();
            contract.validate(result, false);
            status().is(attempt == 0 ? 201 : 200).match(result);
            String id = CatalogHttpTest.json(result).get("serviceId").asText();
            if (original == null) original = id;
            assertThat(id).isEqualTo(original);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.catalog_service WHERE tenant_id = ?",
                Long.class, UUID.fromString(tenant))).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT resource_type FROM commercial_reservation.idempotency_key "
                + "WHERE tenant_id = ? AND key = ?", String.class, UUID.fromString(tenant), key)).isEqualTo("catalog_service");
        assertThat(jdbc.queryForObject("SELECT resource_id FROM commercial_reservation.idempotency_key "
                + "WHERE tenant_id = ? AND key = ?", UUID.class, UUID.fromString(tenant), key)).isEqualTo(UUID.fromString(original));
    }
}
