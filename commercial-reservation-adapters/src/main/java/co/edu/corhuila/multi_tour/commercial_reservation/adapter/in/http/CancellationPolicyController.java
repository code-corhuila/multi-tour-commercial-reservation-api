package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangeCancellationPoliciesCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageCancellationPolicies;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentCancellationPolicyChange;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// HU-COM-013 DP-02 (PROPOSED in the contract): the cancellation penalty policies of the tenant. The role gate runs first (CancellationHttpAccess) and the use case checks
// the role again; only the Administrator writes.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class CancellationPolicyController {
    private final ManageCancellationPolicies policies;
    private final IdempotentCancellationPolicyChange changes;

    public CancellationPolicyController(ManageCancellationPolicies policies, IdempotentCancellationPolicyChange changes) {
        this.policies = policies;
        this.changes = changes;
    }

    @GetMapping("/cancellation-policies")
    public ReservationListResponses<CancellationPolicyResponse> list(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        var paging = new PageQuery(query, Set.of("page", "limit"));
        List<CancellationPolicy> rows = policies.read(PaymentActors.of(principal));
        var data = rows.stream().skip((paging.page - 1L) * paging.limit).limit(paging.limit).map(CancellationPolicyResponse::of).toList();
        return new ReservationListResponses<>(data, paging.meta(rows.size()));
    }

    @PutMapping("/cancellation-policies")
    public Map<String, List<CancellationPolicyResponse>> change(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody ChangeCancellationPoliciesRequest request) {
        if (key == null || key.length() < 8 || key.length() > 128) {
            throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        }
        var changed = changes.change(new ChangeCancellationPoliciesCommand(PaymentActors.of(principal), request.reason(), request.values()), key);
        return Map.of("data", changed.stream().map(CancellationPolicyResponse::of).toList());
    }
}
