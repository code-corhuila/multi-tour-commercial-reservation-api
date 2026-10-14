package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = CatalogHttpPersistenceTest.Database.class)
class CatalogTimestampPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    @Test void realClockTimestampsSurviveReadAndIdempotentReplay() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("commercial_reservation_app");
        var claims = TestJwtKeys.claims();
        claims.put("tenantId", UUID.randomUUID().toString());
        claims.put("exp", clock.instant().plusSeconds(300).getEpochSecond());
        String token = TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
        CatalogContract contract = new CatalogContract();
        // Several real instants prevent an accidentally aligned timestamp from hiding precision loss.
        for (int sample = 0; sample < 3; sample++) {
            String key = UUID.randomUUID().toString();
            var create = mvc.perform(post(CatalogHttpTest.BASE).header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                    .content(TestJwtKeys.JSON.writeValueAsBytes(CatalogHttpTest.body()))).andReturn();
            contract.validate(create, false);
            status().isCreated().match(create);
            var original = CatalogHttpTest.json(create);
            var read = mvc.perform(get(CatalogHttpTest.BASE + "/" + original.get("serviceId").asText())
                    .header("Authorization", "Bearer " + token)).andReturn();
            contract.validate(read, false);
            status().isOk().match(read);
            var replay = mvc.perform(post(CatalogHttpTest.BASE).header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                    .content(TestJwtKeys.JSON.writeValueAsBytes(CatalogHttpTest.body()))).andReturn();
            contract.validate(replay, false);
            status().isOk().match(replay);
            for (String field : new String[] {"createdAt", "updatedAt"}) {
                assertThat(CatalogHttpTest.json(read).get(field)).as("GET %s", field).isEqualTo(original.get(field));
                assertThat(CatalogHttpTest.json(replay).get(field)).as("replay %s", field).isEqualTo(original.get(field));
            }
        }
    }
}
