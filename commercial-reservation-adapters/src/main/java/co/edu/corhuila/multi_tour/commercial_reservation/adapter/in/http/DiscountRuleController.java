package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.InactivateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// PATCH is not mapped on purpose: no update use case exists until the editable fields are closed (HU-COM-003).
// Nothing deletes a rule (RN-13) and there is no reactivation (DP-07).
@RestController
@RequestMapping("/api/v1/commercial-reservation/discount-rules")
public final class DiscountRuleController {
    private static final String BASE = "/api/v1/commercial-reservation/discount-rules/";
    private final IdempotentDiscountRuleCreation creation;
    private final GetDiscountRule get;
    private final InactivateDiscountRule inactivate;

    public DiscountRuleController(IdempotentDiscountRuleCreation creation, GetDiscountRule get,
            InactivateDiscountRule inactivate) {
        this.creation = creation;
        this.get = get;
        this.inactivate = inactivate;
    }

    @PostMapping
    public ResponseEntity<DiscountRuleResponse> create(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody CreateDiscountRuleRequest request) {
        if (key == null || key.length() < 8 || key.length() > 128) {
            throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        }
        var result = creation.create(DiscountRuleHttpAccess.actor(principal), request.toCommand(), key);
        var body = DiscountRuleResponse.from(result.rule());
        return result.created() ? ResponseEntity.created(URI.create(BASE + body.discountRuleId())).body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping("/{discountRuleId}")
    public DiscountRuleResponse read(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("discountRuleId") UUID ruleId) {
        return DiscountRuleResponse.from(get.get(DiscountRuleHttpAccess.actor(principal), ruleId));
    }

    @PostMapping("/{discountRuleId}/inactivate")
    public DiscountRuleResponse inactivate(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("discountRuleId") UUID ruleId) {
        return DiscountRuleResponse.from(inactivate.inactivate(DiscountRuleHttpAccess.actor(principal), ruleId));
    }
}
