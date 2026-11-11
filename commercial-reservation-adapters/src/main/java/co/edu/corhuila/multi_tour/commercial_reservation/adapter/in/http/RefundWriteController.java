package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
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

// The writes of HU-COM-016 (PROPOSED in the contract, AC-16.1 to AC-16.3, AC-16.12, AC-16.17): the Administrator records the 100 % decision, authorizes and rejects
// (RefundHttpAccess refuses everyone else first). The amount, the origin, the mode, the person and the instant are never read from the body: any field the contract
// does not name is a 400 that names it. Every write needs its Idempotency-Key.
@RestController
@RequestMapping("/api/v1/commercial-reservation/financial-decisions/{financialDecisionId}")
public final class RefundWriteController {
    private final IdempotentRefundWrites writes;

    public RefundWriteController(IdempotentRefundWrites writes) {
        this.writes = writes;
    }

    @PostMapping("/refund-decision")
    public FinancialDecisionResponse record(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("financialDecisionId") UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) Map<String, Object> body) {
        fields(body, "");
        return FinancialDecisionResponse.of(writes.record(PaymentActors.of(principal), key(key), id).value());
    }

    @PostMapping("/refund-authorization")
    public ResponseEntity<RefundResponse> authorize(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("financialDecisionId") UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = fields(body, "reason");
        var outcome = writes.authorize(PaymentActors.of(principal), key(key), id, reason);
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(RefundResponse.of(outcome.value()));
    }

    @PostMapping("/rejection")
    public FinancialDecisionResponse reject(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("financialDecisionId") UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) Map<String, Object> body) {
        String justification = fields(body, "justification");
        return FinancialDecisionResponse.of(writes.reject(PaymentActors.of(principal), key(key), id, justification).value());
    }

    private static String key(String key) {
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        return key;
    }

    // The only field allowed ("" for none); returns its text, or null when none is named. A blank text is a 400 that names the field.
    private static String fields(Map<String, Object> body, String allowed) {
        if (body != null) {
            for (String field : body.keySet()) if (!field.equals(allowed)) throw new DomainException(field, field + " is not accepted");
        }
        if (allowed.isEmpty()) return null;
        Object value = body == null ? null : body.get(allowed);
        if (!(value instanceof String text) || text.isBlank()) throw new DomainException(allowed, allowed + " is required");
        return text;
    }
}
