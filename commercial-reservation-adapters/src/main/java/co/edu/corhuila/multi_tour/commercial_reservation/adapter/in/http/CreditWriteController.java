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

// The writes of HU-COM-017 (PROPOSED in the contract, AC-17.2, AC-17.4, AC-17.8, AC-17.13 to AC-17.15): the Administrator records the 100 % credit of a decision, and
// staff or the owner customer apply a credit to a reservation. CreditHttpAccess refuses the other roles first. The person, the capacity and the instant are never read
// from the body: any field the contract does not name is a 400 that names it. Every write needs its Idempotency-Key.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class CreditWriteController {
    private final IdempotentCreditWrites writes;

    public CreditWriteController(IdempotentCreditWrites writes) {
        this.writes = writes;
    }

    @PostMapping("/financial-decisions/{financialDecisionId}/credit-decision")
    public CreditBalanceResponse recordDecision(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("financialDecisionId") UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body != null && !body.isEmpty()) throw new DomainException(body.keySet().iterator().next(), "the body must be empty");
        return CreditBalanceResponse.of(writes.recordDecision(PaymentActors.of(principal), key(key), id).value());
    }

    @PostMapping("/credit-balances/{creditBalanceId}/applications")
    public ResponseEntity<CreditApplicationResponse> apply(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("creditBalanceId") UUID creditId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body == null) throw new DomainException("targetReservationId", "targetReservationId is required");
        for (String field : body.keySet()) {
            if (!field.equals("targetReservationId") && !field.equals("amountCents")) throw new DomainException(field, field + " is not accepted");
        }
        UUID target = uuid(body.get("targetReservationId"), "targetReservationId");
        if (!(body.get("amountCents") instanceof Number amount) || amount.longValue() != amount.doubleValue() || amount.longValue() <= 0) {
            throw new DomainException("amountCents", "amountCents must be a whole number above zero");
        }
        var outcome = writes.apply(PaymentActors.of(principal), key(key), creditId, target, amount.longValue());
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(CreditApplicationResponse.of(outcome.value()));
    }

    @PostMapping("/credit-balances/{creditBalanceId}/refund-requests")
    public ResponseEntity<FinancialDecisionResponse> requestRefund(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("creditBalanceId") UUID creditId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body == null) throw new DomainException("amountCents", "amountCents is required");
        for (String field : body.keySet()) {
            if (!field.equals("amountCents") && !field.equals("reason")) throw new DomainException(field, field + " is not accepted");
        }
        if (!(body.get("amountCents") instanceof Number amount) || amount.longValue() != amount.doubleValue() || amount.longValue() <= 0) {
            throw new DomainException("amountCents", "amountCents must be a whole number above zero");
        }
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        var outcome = writes.requestRefund(PaymentActors.of(principal), key(key), creditId, amount.longValue(), reason);
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(FinancialDecisionResponse.of(outcome.value()));
    }

    private static String key(String key) {
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        return key;
    }

    private static UUID uuid(Object value, String field) {
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(field, field + " must be a UUID");
        }
    }
}
