package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.test.context.ContextConfiguration(initializers = TestJwtKeys.class)
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void healthAnswers200WithoutAnyToken() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void healthCarriesAnRfc3339UtcTimestamp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-08T12:00:00Z"));
    }
}
