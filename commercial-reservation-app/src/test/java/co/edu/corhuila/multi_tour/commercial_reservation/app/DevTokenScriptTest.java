package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.AuthenticatedPrincipal;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.InvalidTokenException;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.Rs256Verifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Runs scripts/dev-token.sh with a throwaway key generated here; requires bash and openssl.
class DevTokenScriptTest {
    private static final Path SCRIPT = Path.of("..", "scripts", "dev-token.sh").toAbsolutePath().normalize();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TENANT = "00000000-0000-0000-0000-000000000123";
    private static final String SUB = "00000000-0000-0000-0000-000000000456";

    @TempDir
    Path dir;

    @Test
    void tokenIsAcceptedByTheRepositoryVerifier() throws Exception {
        KeyPair pair = TestJwtKeys.generate();
        String token = run(pair, "--tenant", TENANT, "--sub", SUB, "--roles", "TENANT_ADMINISTRATOR,COLLABORATOR",
                "--permissions", "catalog:read,catalog:write", "--ttl", "600");

        AuthenticatedPrincipal principal = new Rs256Verifier(pair.getPublic(), Clock.systemUTC(), JSON).verify(token);

        assertThat(principal.sub()).isEqualTo(SUB);
        assertThat(principal.tenantId()).isEqualTo(UUID.fromString(TENANT));
        assertThat(principal.roles()).isEqualTo(Set.of("TENANT_ADMINISTRATOR", "COLLABORATOR"));
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(token.split("[.]")[1]));
        assertThat(claims.path("permissions")).extracting(JsonNode::textValue)
                .containsExactly("catalog:read", "catalog:write");
        assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(600);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        KeyPair signer = TestJwtKeys.generate();
        String token = run(signer, "--tenant", TENANT, "--sub", SUB, "--roles", "COLLABORATOR");

        Rs256Verifier verifier = new Rs256Verifier(TestJwtKeys.generate().getPublic(), Clock.systemUTC(), JSON);
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidTokenException.class);
    }

    private String run(KeyPair pair, String... args) throws Exception {
        Path key = dir.resolve("private-" + UUID.randomUUID() + ".pem");
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(pair.getPrivate().getEncoded());
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");
        List<String> command = new java.util.ArrayList<>(List.of("bash", SCRIPT.toString(), "--key", key.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertThat(process.waitFor()).as(output).isZero();
        return output;
    }
}
