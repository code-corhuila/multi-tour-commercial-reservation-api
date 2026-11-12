package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentRefundDetermination;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExecuteRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RefundAutomation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.OutflowMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The internal routes of the refund (PROPOSED in the contract, plan D-06, DP-08, DP-15): the steps of the saga execute-refund (start, undo, completion), the
// default and the overdue mark of job 2, and the determination of operations. The SERVICE role and nothing else; the tenant is the path because a service
// token has none. The workflow and the job ask and the service decides; a step repeated with the same effect answers 200 and changes nothing.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal/tenants/{tenantId}")
public final class RefundInternalController {
    public record StartRequest(UUID executedBy, String outflowMethod) {}
    public record CompletionRequest(UUID cashMovementId) {}
    public record DeterminationRequest(UUID reservationId, String origin, Long amountCents) {}

    private final ExecuteRefund execute;
    private final RefundAutomation automation;
    private final IdempotentRefundDetermination determination;

    public RefundInternalController(ExecuteRefund execute, RefundAutomation automation, IdempotentRefundDetermination determination) {
        this.execute = execute;
        this.automation = automation;
        this.determination = determination;
    }

    @PostMapping("/refund-authorizations/{refundAuthorizationId}/execution-start")
    public RefundResponse start(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @PathVariable("refundAuthorizationId") UUID id, @RequestBody StartRequest body) {
        requireService(principal);
        if (body.executedBy() == null) throw new DomainException("executedBy", "executedBy is required");
        return RefundResponse.of(execute.start(tenantId, id, body.executedBy(), method(body.outflowMethod())));
    }

    @PostMapping("/refund-authorizations/{refundAuthorizationId}/execution-undo")
    public RefundResponse undo(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @PathVariable("refundAuthorizationId") UUID id) {
        requireService(principal);
        return RefundResponse.of(execute.undo(tenantId, id));
    }

    @PostMapping("/refund-authorizations/{refundAuthorizationId}/execution-completion")
    public RefundResponse complete(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @PathVariable("refundAuthorizationId") UUID id, @RequestBody CompletionRequest body) {
        requireService(principal);
        if (body.cashMovementId() == null) throw new DomainException("cashMovementId", "cashMovementId is required");
        return RefundResponse.of(execute.complete(tenantId, id, body.cashMovementId()));
    }

    @PostMapping("/refund-authorizations/{refundAuthorizationId}/overdue-mark")
    public RefundResponse overdue(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @PathVariable("refundAuthorizationId") UUID id) {
        requireService(principal);
        return RefundResponse.of(automation.markOverdue(tenantId, id));
    }

    @PostMapping("/financial-decisions/{financialDecisionId}/default")
    public RefundResponse applyDefault(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @PathVariable("financialDecisionId") UUID id) {
        requireService(principal);
        return RefundResponse.of(automation.applyDefault(tenantId, id));
    }

    @PostMapping("/refund-determinations")
    public ResponseEntity<RefundResponse> determine(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody DeterminationRequest body) {
        requireService(principal);
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        if (body.reservationId() == null) throw new DomainException("reservationId", "reservationId is required");
        var outcome = determination.determine(tenantId, key, body.reservationId(), origin(body.origin()), body.amountCents());
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(RefundResponse.of(outcome.refund()));
    }

    private static OutflowMethod method(String code) {
        try {
            return OutflowMethod.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("outflowMethod", "outflowMethod is required: TRANSFER or CASH");
        }
    }

    private static RefundOrigin origin(String code) {
        try {
            return RefundOrigin.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("origin", "origin is required");
        }
    }

    private static void requireService(AuthenticatedPrincipal principal) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
