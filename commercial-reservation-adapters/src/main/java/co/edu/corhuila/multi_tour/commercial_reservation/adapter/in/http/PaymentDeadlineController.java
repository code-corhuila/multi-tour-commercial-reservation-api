package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordFollowupCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExtendDeadlineCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPaymentFollowups;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPendingPayments;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

// The public routes of HU-COM-011 (PROPOSED in the contract): the pending list, the deadline, the follow-ups and the single extension.
// The user and the tenant are the verified token and the time is the clock, never the body; the role is checked by the use case before
// anything is read; a service token has no person and is refused here by the actor.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class PaymentDeadlineController {
    // The note is free text about a person: the web layer logs a request body by its toString at DEBUG, so it never prints the note.
    public record FollowupRequest(String note) {
        @Override public String toString() { return "FollowupRequest[note=<hidden>]"; }
    }

    public record ExtensionRequest(Integer hours) {}

    public record PendingRow(UUID reservationId, String customerName, String paymentType, String paymentMethod, long validatedValueCents,
            long pendingBalanceCents, String paymentStatus, String deadlineKind, String effectiveDeadline, boolean hasProofInValidation) {}

    private final ListPendingPayments pending;
    private final GetPaymentDeadline deadline;
    private final ListPaymentFollowups history;
    private final IdempotentDeadlineWrites writes;

    public PaymentDeadlineController(ListPendingPayments pending, GetPaymentDeadline deadline, ListPaymentFollowups history,
            IdempotentDeadlineWrites writes) {
        this.pending = pending;
        this.deadline = deadline;
        this.history = history;
        this.writes = writes;
    }

    @GetMapping("/pending-payments")
    public ReservationListResponses<PendingRow> pending(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        PaymentActor actor = PaymentActors.of(principal);
        var paging = new PageQuery(query, Set.of("page", "limit", "kind"));
        var page = pending.list(actor, kind(query.get("kind")), paging.page, paging.limit);
        var rows = page.items().stream().map(row -> new PendingRow(row.reservationId(), row.customerName(), row.paymentType().name(),
                row.paymentMethod().name(), row.validatedValueCents(), row.pendingBalanceCents(), row.paymentStatus().name(),
                row.deadlineKind().name(), row.effectiveDeadline().toString(), row.hasProofInValidation())).toList();
        return new ReservationListResponses<>(rows, paging.meta(page.total()));
    }

    @GetMapping("/reservations/{reservationId}/payment-deadline")
    public DeadlineResponse read(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId) {
        var actor = PaymentActors.of(principal);
        return DeadlineResponse.of(deadline.get(actor, reservationId), actor.role() != co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role.FINAL_CUSTOMER);
    }

    @GetMapping("/reservations/{reservationId}/payment-followups")
    public ReservationListResponses<FollowupResponse> followups(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestParam Map<String, String> query) {
        PaymentActor actor = PaymentActors.of(principal);
        var paging = new PageQuery(query, Set.of("page", "limit"));
        var page = history.list(actor, reservationId, paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(FollowupResponse::of).toList(), paging.meta(page.total()));
    }

    @PostMapping("/reservations/{reservationId}/payment-followups")
    public ResponseEntity<FollowupResponse> recordFollowup(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody FollowupRequest request) {
        requireKey(key);
        var done = writes.followup(new RecordFollowupCommand(PaymentActors.of(principal), reservationId, request.note()), key);
        return ResponseEntity.status(done.created() ? HttpStatus.CREATED : HttpStatus.OK).body(FollowupResponse.of(done.followup()));
    }

    @PostMapping("/reservations/{reservationId}/payment-deadline/extension")
    public ResponseEntity<DeadlineResponse> extend(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ExtensionRequest request) {
        requireKey(key);
        var done = writes.extension(new ExtendDeadlineCommand(PaymentActors.of(principal), reservationId, request.hours()), key);
        return ResponseEntity.status(done.created() ? HttpStatus.CREATED : HttpStatus.OK).body(DeadlineResponse.of(done.view()));
    }

    private static DeadlineKind kind(String value) {
        if (value == null) return null;
        try {
            return DeadlineKind.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            throw new DomainException("kind", "kind must be INITIAL_PAYMENT, BALANCE or PENDING_DIFFERENCE");
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
    }
}
