package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Rs256Verifier {
    private static final Logger LOG = LoggerFactory.getLogger(Rs256Verifier.class);
    private final PublicKey key;
    private final Clock clock;
    private final ObjectMapper json;

    public Rs256Verifier(PublicKey key, Clock clock, ObjectMapper json) {
        this.key = key;
        this.clock = clock;
        this.json = json;
    }

    public AuthenticatedPrincipal verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            require(parts.length == 3, "compact token must have three parts");
            for (String part : parts) require(part.matches("[A-Za-z0-9_-]+"), "invalid base64url segment");
            JsonNode header = parse(parts[0]);
            require("RS256".equals(header.path("alg").textValue()), "unsupported algorithm");
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            require(signature.verify(Base64.getUrlDecoder().decode(parts[2])), "invalid signature");
            JsonNode claims = parse(parts[1]);
            JsonNode exp = claims.path("exp");
            require(exp.isIntegralNumber() && exp.canConvertToLong(), "missing or invalid expiration");
            require(exp.longValue() > clock.instant().getEpochSecond() - 30, "expired token");
            JsonNode sub = claims.path("sub");
            require(sub.isTextual() && !sub.textValue().isBlank(), "missing or invalid subject");
            JsonNode tenant = claims.path("tenantId");
            UUID tenantId = null;
            if (!tenant.isMissingNode() && !tenant.isNull()) {
                require(tenant.isTextual(), "invalid tenant identifier");
                tenantId = UUID.fromString(tenant.textValue());
                require(tenantId.toString().equalsIgnoreCase(tenant.textValue()), "invalid tenant identifier");
            }
            JsonNode roles = claims.path("roles");
            Set<String> names = new HashSet<>();
            if (!roles.isMissingNode()) {
                require(roles.isArray(), "roles must be an array");
                for (JsonNode role : roles) {
                    require(role.isTextual(), "roles must contain strings");
                    names.add(role.textValue());
                }
            }
            return new AuthenticatedPrincipal(sub.textValue(), tenantId, names);
        } catch (Exception failure) {
            LOG.warn("Access token rejected: {}", failure instanceof IllegalArgumentException
                    ? failure.getMessage() : failure.getClass().getSimpleName());
            throw new InvalidTokenException();
        }
    }

    private JsonNode parse(String segment) throws Exception {
        JsonNode node = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .readTree(Base64.getUrlDecoder().decode(segment));
        require(node != null && node.isObject(), "token JSON must be an object");
        return node;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }
}
