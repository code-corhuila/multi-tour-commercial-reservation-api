package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Tenant Administrator only (the access interceptor runs before the body is bound). A reason is never deleted.
@RestController
@RequestMapping("/api/v1/commercial-reservation/additional-discount-reasons")
public final class AuthorizableReasonController {
    private static final String BASE = "/api/v1/commercial-reservation/additional-discount-reasons/";
    private final TransactionalAuthorizableReasons reasons;

    public AuthorizableReasonController(TransactionalAuthorizableReasons reasons) {
        this.reasons = reasons;
    }

    @GetMapping
    public AuthorizableReasonPageResponse list(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        for (String field : query.keySet()) {
            if (!Set.of("page", "limit").contains(field)) throw new DomainException(field, "unknown query parameter");
        }
        int page = integer(query, "page", 1, Integer.MAX_VALUE);
        int limit = integer(query, "limit", 20, 100);
        return AuthorizableReasonPageResponse.of(reasons.list(DiscountRuleHttpAccess.actor(principal)), page, limit);
    }

    @PostMapping
    public ResponseEntity<AuthorizableReasonResponse> add(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody CreateAuthorizableReasonRequest request) {
        if (key == null || key.length() < 8 || key.length() > 128) {
            throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        }
        var result = reasons.add(DiscountRuleHttpAccess.actor(principal), request.validText(), key);
        var body = AuthorizableReasonResponse.from(result.item());
        return result.created() ? ResponseEntity.created(URI.create(BASE + body.reasonId())).body(body)
                : ResponseEntity.ok(body);
    }

    @PostMapping("/{reasonId}/disable")
    public AuthorizableReasonResponse disable(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reasonId") UUID reasonId) {
        return AuthorizableReasonResponse.from(reasons.disable(DiscountRuleHttpAccess.actor(principal), reasonId));
    }

    private static int integer(Map<String, String> query, String field, int fallback, int maximum) {
        try {
            int value = query.containsKey(field) ? Integer.parseInt(query.get(field)) : fallback;
            if (value < 1 || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new DomainException(field, "must be an integer between 1 and " + maximum);
        }
    }
}
