package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadSensitiveCustomerData;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipher;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.context.support.TestPropertySourceUtils;

// The cipher is built from the two environment variables and the service refuses to start without a usable key.
class FieldEncryptionStartupTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class))
            .withUserConfiguration(CommercialReservationConfiguration.class, CustomerConfiguration.class)
            .withInitializer(context -> {
                // Host credentials must not mask missing-configuration failures.
                context.getEnvironment().getPropertySources().remove("systemEnvironment");
                context.getEnvironment().getPropertySources().remove("systemProperties");
                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                        "DB_URL=jdbc:postgresql://127.0.0.1:1/unavailable", "DB_USER=startup-test",
                        "DB_PASSWORD=startup-test-password");
                context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("jwt", java.util.Map.of("JWT_PUBLIC_KEY", TestJwtKeys.pem(TestJwtKeys.TRUSTED), "JWT_PUBLIC_KEY_FILE", "")));
                new ConfigDataApplicationContextInitializer().initialize(context);
            });

    @Test
    void noKeyStopsStartupAndNamesTheVariables() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("FIELD_ENCRYPTION_KEYS");
        });
    }

    @Test
    void aCurrentKeyIdThatIsNotConfiguredStopsStartup() {
        runner.withPropertyValues("FIELD_ENCRYPTION_CURRENT_KEY_ID=missing", "FIELD_ENCRYPTION_KEYS=k1=" + KEY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void anInvalidKeyStopsStartupWithoutPrintingIt() {
        runner.withPropertyValues("FIELD_ENCRYPTION_CURRENT_KEY_ID=k1", "FIELD_ENCRYPTION_KEYS=k1=secret-not-a-key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("FIELD_ENCRYPTION_KEYS");
                    assertThat(String.valueOf(context.getStartupFailure().getMessage())).doesNotContain("secret-not-a-key");
                });
    }

    @Test
    void aConfiguredKeyBuildsTheCipherAndTheTwoUseCases() {
        runner.withPropertyValues("FIELD_ENCRYPTION_CURRENT_KEY_ID=k1", "FIELD_ENCRYPTION_KEYS=k1=" + KEY)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FieldCipher.class);
                    assertThat(context).hasSingleBean(RegisterLeadCustomer.class);
                    assertThat(context).hasSingleBean(ReadSensitiveCustomerData.class);
                    FieldCipher cipher = context.getBean(FieldCipher.class);
                    assertThat(cipher.decrypt(cipher.encrypt("valor ficticio", "aad"), "aad")).isEqualTo("valor ficticio");
                });
    }
}
