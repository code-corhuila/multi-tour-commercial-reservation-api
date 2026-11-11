package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ConcludeRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentReschedulingWrites;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
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

// The writes of HU-COM-018 (PROPOSED in the contract, AC-18.6, AC-18.7, AC-18.9, AC-18.15, AC-18.17): the proposal, the answer of the customer, the answer received by an outside channel and
// the declaration of no alternative. ReschedulingHttpAccess refuses the wrong role first. The tenant, the person and the recording instant are never read from the body: any field the contract
// does not name is a 400 that names it. The evidence and the channel are never logged and never come back. Every write but the declaration needs its Idempotency-Key.
@RestController
@RequestMapping("/api/v1/commercial-reservation/reschedulings/{reschedulingId}")
public final class ReschedulingWriteController {
    private final IdempotentReschedulingWrites writes;
    private final ConcludeRescheduling conclusion;

    public ReschedulingWriteController(IdempotentReschedulingWrites writes, ConcludeRescheduling conclusion) {
        this.writes = writes;
        this.conclusion = conclusion;
    }

    @PostMapping("/proposals")
    public ResponseEntity<ReschedulingResponse> propose(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reschedulingId") UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody(required = false) Map<String, Object> body) {
        UUID departure = uuid(text(fields(body, "proposedDepartureId"), "proposedDepartureId"), "proposedDepartureId");
        return reply(writes.propose(PaymentActors.of(principal), key(key), id, departure));
    }

    @PostMapping("/no-alternative")
    public ReschedulingResponse noAlternative(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("reschedulingId") UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        fields(body);
        var actor = PaymentActors.of(principal);
        return ReschedulingResponse.of(conclusion.declareNoAlternative(actor.tenantId(), actor.actorId(), actor.role(), id));
    }

    @PostMapping("/proposals/{proposalId}/answer")
    public ReschedulingResponse answer(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("reschedulingId") UUID id,
            @PathVariable("proposalId") UUID proposalId, @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody(required = false) Map<String, Object> body) {
        boolean accepted = decision(fields(body, "decision"));
        return ReschedulingResponse.of(writes.answer(PaymentActors.of(principal), key(key), id, proposalId, accepted).episode());
    }

    @PostMapping("/proposals/{proposalId}/external-answer")
    public ResponseEntity<ReschedulingResponse> externalAnswer(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("reschedulingId") UUID id,
            @PathVariable("proposalId") UUID proposalId, @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> fields = fields(body, "decision", "channel", "evidence", "answeredAt");
        boolean accepted = decision(fields);
        String channel = text(fields, "channel");
        String evidence = text(fields, "evidence");
        Instant answeredAt;
        try {
            answeredAt = Instant.parse(text(fields, "answeredAt"));
        } catch (DateTimeParseException invalid) {
            throw new DomainException("answeredAt", "answeredAt must be an instant");
        }
        return reply(writes.recordOutside(PaymentActors.of(principal), key(key), id, proposalId, accepted, channel, evidence, answeredAt));
    }

    private static ResponseEntity<ReschedulingResponse> reply(IdempotentReschedulingWrites.Outcome outcome) {
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(ReschedulingResponse.of(outcome.episode()));
    }

    private static String key(String key) {
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        return key;
    }

    // The body, closed: a field the contract does not name is a 400 that names it.
    private static Map<String, Object> fields(Map<String, Object> body, String... allowed) {
        Map<String, Object> known = body == null ? Map.of() : body;
        Set<String> names = Set.of(allowed);
        for (String field : known.keySet()) if (!names.contains(field)) throw new DomainException(field, field + " is not accepted");
        return known;
    }

    private static String text(Map<String, Object> fields, String name) {
        if (!(fields.get(name) instanceof String value) || value.isBlank()) throw new DomainException(name, name + " is required");
        return value;
    }

    private static boolean decision(Map<String, Object> fields) {
        String value = text(fields, "decision");
        if (!value.equals("ACCEPT") && !value.equals("REJECT")) throw new DomainException("decision", "decision must be ACCEPT or REJECT");
        return value.equals("ACCEPT");
    }

    private static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(field, field + " must be a UUID");
        }
    }
}
