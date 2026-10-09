package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
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
