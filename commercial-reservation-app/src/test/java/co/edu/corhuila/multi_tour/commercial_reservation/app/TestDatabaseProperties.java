package co.edu.corhuila.multi_tour.commercial_reservation.app;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

public class TestDatabaseProperties implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    // A throw-away key per test process: the cipher fails closed when no key is configured.
    private static final String FIELD_KEY = randomKey();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        // Startup tests exercise wiring without opening the lazy connection pool.
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "DB_URL=jdbc:postgresql://127.0.0.1:1/unavailable", "DB_USER=startup-test",
                "DB_PASSWORD=" + UUID.randomUUID(),
                "FIELD_ENCRYPTION_CURRENT_KEY_ID=test", "FIELD_ENCRYPTION_KEYS=test=" + FIELD_KEY);
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
