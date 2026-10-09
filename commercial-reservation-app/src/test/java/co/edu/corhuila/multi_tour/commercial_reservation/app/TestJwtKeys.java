package co.edu.corhuila.multi_tour.commercial_reservation.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

// Keys exist only for this test process; no signing material is read from the repository.
public class TestJwtKeys implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    static final String TENANT = "00000000-0000-0000-0000-000000000123";
    static final KeyPair TRUSTED = generate();
    static final KeyPair OTHER = generate();
    static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "JWT_PUBLIC_KEY=" + pem(TRUSTED).replace("\n", "\\n"), "JWT_PUBLIC_KEY_FILE=");
    }

    static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception failure) {
            throw new IllegalStateException("cannot generate test keys", failure);
        }
    }

    static String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    static Map<String, Object> claims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "test-account");
        claims.put("exp", NOW.plusSeconds(300).getEpochSecond());
        claims.put("tenantId", TENANT);
        claims.put("roles", List.of("TENANT_ADMINISTRATOR"));
        return claims;
    }

    static String token() throws Exception {
        return token(Map.of("alg", "RS256"), claims(), TRUSTED);
    }

    static String token(Map<String, Object> header, Map<String, Object> claims, KeyPair signer) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String input = encoder.encodeToString(JSON.writeValueAsBytes(header)) + "."
                + encoder.encodeToString(JSON.writeValueAsBytes(claims));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(signer.getPrivate());
        signature.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + encoder.encodeToString(signature.sign());
    }
}
