package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterCashCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterTransferCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetOwnReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentAccessDeniedException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// The payments of a reservation (PROPOSED in the contract): one POST with two bodies (multipart for a transfer with its proof, JSON for
// cash) and the read of one attempt. The role is checked before the body (PaymentHttpAccess); the user and the tenant are the token.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class PaymentController {
    private static final String RESERVATIONS = "/api/v1/commercial-reservation/reservations/";
    private final IdempotentPaymentRegistration registration;
    private final GetReservation get;
    private final GetOwnReservation getOwn;

    public PaymentController(IdempotentPaymentRegistration registration, GetReservation get, GetOwnReservation getOwn) {
        this.registration = registration;
        this.get = get;
        this.getOwn = getOwn;
    }

    @PostMapping(value = "/reservations/{reservationId}/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> registerCash(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody RegisterPaymentRequest request) {
        return register(principal, reservationId, key, request, null, false);
    }

    @PostMapping(value = "/reservations/{reservationId}/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentResponse> registerTransfer(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestPart(value = "payment", required = false) RegisterPaymentRequest request,
            @RequestPart(value = "proof", required = false) MultipartFile proof) throws IOException {
        if (request == null) throw new DomainException("payment", "the payment part is required");
        return register(principal, reservationId, key, request, proof == null ? null : proof.getBytes(), true);
    }

    @GetMapping("/reservations/{reservationId}/payments/{paymentId}")
    public PaymentResponse read(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @PathVariable("paymentId") UUID paymentId) {
        Reservation reservation = reservation(PaymentActors.of(principal), reservationId);
        return reservation.attempts().stream().filter(found -> found.attemptId().equals(paymentId)).findFirst()
                .map(attempt -> PaymentResponse.of(reservation, attempt)).orElseThrow(() -> new PaymentProofNotFoundException(paymentId));
    }

    private ResponseEntity<PaymentResponse> register(AuthenticatedPrincipal principal, UUID reservationId, String key,
            RegisterPaymentRequest request, byte[] proof, boolean multipart) {
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        PaymentActor actor = PaymentActors.of(principal);
        PaymentMethod method = request.method();
        if (actor.role() == PaymentActor.Role.FINAL_CUSTOMER) {
            if (method == PaymentMethod.CASH) throw new PaymentAccessDeniedException(); // cash is received by the office only
            reservation(actor, reservationId); // the own reservation, or it looks missing
        }
        if (multipart && method == PaymentMethod.CASH) throw new DomainException("proof", "a cash payment carries no file");
        var result = method == PaymentMethod.TRANSFER
                ? registration.transfer(new RegisterTransferCommand(actor.tenantId(), actor.actorId(), reservationId, request.amountCents(), proof), key)
                : registration.cash(new RegisterCashCommand(actor.tenantId(), actor.actorId(), reservationId, request.amountCents()), key);
        var body = PaymentResponse.of(result.reservation(), result.attempt());
        return result.created() ? ResponseEntity.created(URI.create(RESERVATIONS + reservationId + "/payments/" + body.paymentId())).body(body)
                : ResponseEntity.ok(body);
    }

    // A Final Customer reads through the own scope, so a reservation of another customer looks missing.
    private Reservation reservation(PaymentActor actor, UUID reservationId) {
        return actor.role() == PaymentActor.Role.FINAL_CUSTOMER ? getOwn.get(actor.tenantId(), actor.actorId(), reservationId)
                : get.get(actor.tenantId(), reservationId);
    }
}
