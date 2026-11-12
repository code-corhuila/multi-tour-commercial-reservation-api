package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// HU-SEC-007 G-SEC9-1: the verifier reads access, scope and authorizationId of an exceptional or emergency token; a token that claims such an access without them, or with bad ones, is not valid.
class Rs256VerifierExceptionalAccessTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID AUTHORIZATION = UUID.randomUUID();
    private static KeyPair keys;
    private final Rs256Verifier verifier = new Rs256Verifier(keys.getPublic(), Clock.fixed(NOW, ZoneOffset.UTC), JSON);

    @BeforeAll
    static void generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String token(Map<String, Object> extra) throws Exception {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", UUID.randomUUID().toString());
        claims.put("tenantId", TENANT.toString());
        claims.put("roles", List.of("PLATFORM_ADMINISTRATOR"));
        claims.put("exp", NOW.getEpochSecond() + 3600);
        claims.putAll(extra);
        String head = b64("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String body = b64(JSON.writeValueAsBytes(claims));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keys.getPrivate());
        signature.update((head + "." + body).getBytes(StandardCharsets.US_ASCII));
        return head + "." + body + "." + b64(signature.sign());
    }

    @Test
    void anExceptionalTokenCarriesItsAccessScopeAndAuthorization() throws Exception {
        AuthenticatedPrincipal principal = verifier.verify(token(Map.of("access", "exceptional", "scope", List.of("RESERVATIONS", "CATALOG"),
                "authorizationId", AUTHORIZATION.toString())));

        assertThat(principal.exceptional()).isNotNull();
        assertThat(principal.exceptional().access()).isEqualTo("exceptional");
        assertThat(principal.exceptional().scope()).containsExactlyInAnyOrder("RESERVATIONS", "CATALOG");
        assertThat(principal.exceptional().authorizationId()).isEqualTo(AUTHORIZATION);
        assertThat(principal.restricted()).isFalse();
    }

    @Test
    void anEmergencyTokenMayNameTheSensitiveArea() throws Exception {
        AuthenticatedPrincipal principal = verifier.verify(token(Map.of("access", "emergency", "scope", List.of("SENSITIVE"),
                "authorizationId", AUTHORIZATION.toString())));

        assertThat(principal.exceptional().access()).isEqualTo("emergency");
        assertThat(principal.exceptional().scope()).containsExactly("SENSITIVE");
    }

    @Test
    void anOrdinaryOrRestrictedTokenHasNoExceptionalAccess() throws Exception {
        assertThat(verifier.verify(token(Map.of())).exceptional()).isNull();
        assertThat(verifier.verify(token(Map.of("access", "restricted"))).exceptional()).isNull();
    }

    @Test
    void anExceptionalTokenWithoutOrWithABadClaimIsRefused() throws Exception {
        String id = AUTHORIZATION.toString();
        assertThatThrownBy(() -> verifier.verify(token(Map.of("access", "exceptional", "authorizationId", id)))).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> verifier.verify(token(Map.of("access", "exceptional", "scope", List.of("CASH"))))).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> verifier.verify(token(Map.of("access", "exceptional", "scope", List.of("CASH"), "authorizationId", "not-a-uuid")))).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> verifier.verify(token(Map.of("access", "exceptional", "scope", "CASH", "authorizationId", id)))).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> verifier.verify(token(Map.of("access", "exceptional", "scope", List.of("EVERYTHING"), "authorizationId", id)))).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> verifier.verify(token(Map.of("access", "exceptional", "scope", List.of(), "authorizationId", id)))).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void anExceptionalTokenWithoutTenantIsRefused() throws Exception {
        Map<String, Object> claims = new LinkedHashMap<>(Map.of("access", "emergency", "scope", List.of("CASH"), "authorizationId", AUTHORIZATION.toString()));
        claims.put("tenantId", null);
        assertThatThrownBy(() -> verifier.verify(token(claims))).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void anExceptionalTokenWhoseSubjectIsNotTheIdentifierOfTheAdministratorIsRefused() throws Exception {
        Map<String, Object> claims = new LinkedHashMap<>(Map.of("access", "exceptional", "scope", List.of("CASH"), "authorizationId", AUTHORIZATION.toString(), "sub", "platform-admin-1"));

        assertThatThrownBy(() -> verifier.verify(token(claims))).isInstanceOf(InvalidTokenException.class);
    }
}
