package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

class JwtKeyStartupTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class))
            .withUserConfiguration(CommercialReservationConfiguration.class, TenantStatusConfiguration.class,
                    DepartureMirrorConfiguration.class)
            .withInitializer(context -> {
                // Host credentials must not mask missing-configuration failures.
                context.getEnvironment().getPropertySources().remove("systemEnvironment");
                context.getEnvironment().getPropertySources().remove("systemProperties");
                new TestDatabaseProperties().initialize(context);
                new ConfigDataApplicationContextInitializer().initialize(context);
            });

    @Test
    void missingPublicKeyStopsStartupAndNamesBothVariables() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("JWT_PUBLIC_KEY")
                    .hasStackTraceContaining("JWT_PUBLIC_KEY_FILE");
        });
    }

    @Test
    void aGeneratedPublicKeyFileAllowsStartup() throws Exception {
        Path directory = Files.createDirectories(Path.of("target"));
        Path file = Files.createTempFile(directory, "jwt-test-", ".key");
        try {
            Files.writeString(file, TestJwtKeys.pem(TestJwtKeys.TRUSTED));
            runner.withPropertyValues("JWT_PUBLIC_KEY_FILE=" + file.toAbsolutePath()).run(context ->
                    assertThat(context).hasNotFailed());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void anInvalidKeyStopsStartupWithoutPrintingIt() {
        runner.withPropertyValues("JWT_PUBLIC_KEY=secret-invalid-key").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("JWT_PUBLIC_KEY")
                    .hasStackTraceContaining("JWT_PUBLIC_KEY_FILE");
            assertThat(context.getStartupFailure()).hasStackTraceContaining("cannot load")
                    .hasStackTraceContaining("RSA");
            assertThat(context.getStartupFailure().getMessage()).doesNotContain("secret-invalid-key");
        });
    }

    @Test
    void inlineKeyTakesPrecedenceOverAnInvalidFile() {
        runner.withPropertyValues("JWT_PUBLIC_KEY=" + TestJwtKeys.pem(TestJwtKeys.TRUSTED).replace("\n", "\\n"),
                "JWT_PUBLIC_KEY_FILE=does-not-exist").run(context -> assertThat(context).hasNotFailed());
    }
}
