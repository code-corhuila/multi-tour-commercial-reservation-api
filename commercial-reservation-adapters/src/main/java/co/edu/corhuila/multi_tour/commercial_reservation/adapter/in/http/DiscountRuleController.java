package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentDiscountRuleCreation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.InactivateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReactivateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// PATCH (updateDiscountRule) and reactivate exist since the decision of 2026-11-03 (HU-COM-003); nothing deletes a rule (RN-13).
@RestController
@RequestMapping("/api/v1/commercial-reservation/discount-rules")
public final class DiscountRuleController {
    private static final String BASE = "/api/v1/commercial-reservation/discount-rules/";
    private final IdempotentDiscountRuleCreation creation;
    private final GetDiscountRule get;
    private final InactivateDiscountRule inactivate;
    private final UpdateDiscountRule update;
    private final ReactivateDiscountRule reactivate;
    private final ListDiscountRules list;

    public DiscountRuleController(IdempotentDiscountRuleCreation creation, GetDiscountRule get,
            InactivateDiscountRule inactivate, UpdateDiscountRule update, ReactivateDiscountRule reactivate,
            ListDiscountRules list) {
        this.creation = creation;
        this.get = get;
        this.inactivate = inactivate;
        this.update = update;
        this.reactivate = reactivate;
        this.list = list;
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

    @GetMapping
    public DiscountRulePageResponse list(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        return DiscountRulePageResponse.of(list.list(DiscountRuleHttpAccess.actor(principal)), query);
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

    @PostMapping("/{discountRuleId}/reactivate")
    public DiscountRuleResponse reactivate(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("discountRuleId") UUID ruleId) {
        return DiscountRuleResponse.from(reactivate.reactivate(DiscountRuleHttpAccess.actor(principal), ruleId));
    }

    // JSON merge: the body is read as a tree so an absent field and a null stay different.
    @PatchMapping("/{discountRuleId}")
    public DiscountRuleResponse update(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("discountRuleId") UUID ruleId, @RequestBody JsonNode body) {
        return DiscountRuleResponse.from(update.update(DiscountRuleHttpAccess.actor(principal), ruleId,
                UpdateDiscountRuleRequest.toPatch(body)));
    }
}
