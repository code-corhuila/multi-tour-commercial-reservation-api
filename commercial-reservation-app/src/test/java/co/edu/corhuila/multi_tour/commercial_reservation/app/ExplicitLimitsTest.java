package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

// Annex C: a default value that is not declared is not a decision. Values follow ADR-004 D10.
@org.springframework.test.context.ContextConfiguration(initializers = TestJwtKeys.class)
@SpringBootTest
class ExplicitLimitsTest {

    @Autowired
    private Environment env;

    private Duration duration(String key) {
        return env.getRequiredProperty(key, Duration.class);
    }

    @Test
    void headerReadIsFiveSeconds() {
        assertEquals(Duration.ofSeconds(5), duration("server.tomcat.connection-timeout"));
    }

    @Test
    void idleKeepAliveIsSixtySeconds() {
        assertEquals(Duration.ofSeconds(60), duration("server.tomcat.keep-alive-timeout"));
    }

    @Test
    void writeIsFifteenSeconds() {
        assertEquals(Duration.ofSeconds(15), duration("spring.mvc.async.request-timeout"));
    }

    @Test
    void poolIsTenConnections() {
        assertEquals(10, env.getRequiredProperty("spring.datasource.hikari.maximum-pool-size", Integer.class));
    }

    @Test
    void connectionWaitIsFiveSeconds() {
        assertEquals(5000L, env.getRequiredProperty("spring.datasource.hikari.connection-timeout", Long.class));
    }

    @Test
    void statementTimeoutIsFiveSeconds() {
        assertEquals("SET statement_timeout = 5000",
                env.getRequiredProperty("spring.datasource.hikari.connection-init-sql"));
    }

    @Test
    void gracefulShutdownIsTwentySeconds() {
        assertEquals("graceful", env.getRequiredProperty("server.shutdown"));
        assertEquals(Duration.ofSeconds(20), duration("spring.lifecycle.timeout-per-shutdown-phase"));
    }

    @Test
    void aMissingDatabasePasswordHasNoDefaultAndNamesTheVariable() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> env.getProperty("spring.datasource.password"));
        assertTrue(failure.getMessage().contains("DB_PASSWORD"));
    }
}
