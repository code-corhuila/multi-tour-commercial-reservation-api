package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApproveProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordValidatedPaymentCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DecidePaymentProofWithKey;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RecordValidatedPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadCustomerName;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The decisions on a proof (PROPOSED in the contract). The approval and the rejection are the human decision of the reviewer; step 2
// of the saga register-payment records the validated payment through an internal route for the SERVICE role (HU-COM-010 DP-11).
// The reviewer, the time and the state are never read from the body of a person; the saga forwards the user it acts for.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class PaymentProofDecisionController {
    public record Approve(Long verifiedAmountCents, String reason) {}
    public record Reject(String reason) {}
    public record ValidatedPayment(UUID approvedBy, Long verifiedAmountCents, String reason) {}

    private final DecidePaymentProofWithKey decisions;
    private final RecordValidatedPayment validated;
    private final ReadCustomerName customers;
    private final Clock clock;

    public PaymentProofDecisionController(DecidePaymentProofWithKey decisions, RecordValidatedPayment validated, ReadCustomerName customers,
            Clock clock) {
        this.decisions = decisions;
        this.validated = validated;
        this.customers = customers;
        this.clock = clock;
    }

    @PostMapping("/payment-proofs/{paymentId}/approve")
    public ProofDecisionResponse approve(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("paymentId") UUID paymentId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody Approve request) {
        requireKey(key);
        PaymentActor actor = PaymentActors.of(principal);
        return answer(decisions.approve(new ApproveProofCommand(actor, paymentId, request.verifiedAmountCents(), request.reason()), key));
    }

    @PostMapping("/payment-proofs/{paymentId}/reject")
    public ProofDecisionResponse reject(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("paymentId") UUID paymentId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody Reject request) {
        requireKey(key);
        return answer(decisions.reject(new RejectProofCommand(PaymentActors.of(principal), paymentId, request.reason()), key));
    }

    @PostMapping("/internal/tenants/{tenantId}/payment-proofs/{paymentId}/validated-payment")
    public ProofDecisionResponse recordValidated(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @PathVariable("paymentId") UUID paymentId, @RequestBody ValidatedPayment request) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (request.approvedBy() == null) throw new DomainException("approvedBy", "approvedBy is required");
        return answer(validated.record(new RecordValidatedPaymentCommand(tenantId, request.approvedBy(), paymentId,
                request.verifiedAmountCents(), request.reason())));
    }

    private ProofDecisionResponse answer(DecidedProof decided) {
        var reservation = decided.reservation();
        String name = customers.nameOf(reservation.tenantId(), reservation.customerId());
        return ProofDecisionResponse.of(decided, name, Instant.now(clock));
    }

    private static void requireKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
    }
}
