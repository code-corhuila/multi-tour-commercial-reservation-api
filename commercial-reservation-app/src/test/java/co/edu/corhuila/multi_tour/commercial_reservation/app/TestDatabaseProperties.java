package co.edu.corhuila.multi_tour.commercial_reservation.app;

import java.util.UUID;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

public class TestDatabaseProperties implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        // Startup tests exercise wiring without opening the lazy connection pool.
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "DB_URL=jdbc:postgresql://127.0.0.1:1/unavailable", "DB_USER=startup-test",
                "DB_PASSWORD=" + UUID.randomUUID());
    }
}
