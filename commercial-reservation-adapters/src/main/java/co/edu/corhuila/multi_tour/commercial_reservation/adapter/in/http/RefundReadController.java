package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadRefunds;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The reads of HU-COM-016 (PROPOSED in the contract, AC-16.13, AC-16.14): decisions and refunds for the Administrator and the Collaborator (RefundHttpAccess
// runs first), and the two lists of job 2 for the service token. The lists name candidates; the default and the overdue mark re-check each one.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class RefundReadController {
    private final ReadRefunds read;

    public RefundReadController(ReadRefunds read) {
        this.read = read;
    }

    @GetMapping("/financial-decisions")
    public ReservationListResponses<FinancialDecisionResponse> decisions(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        var paging = new PageQuery(query, Set.of("page", "limit", "kind", "pending", "attention"));
        var page = read.decisions(PaymentActors.of(principal), choice(DecisionKind.class, query, "kind"), flag(query, "pending"), flag(query, "attention"), paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(FinancialDecisionResponse::of).toList(), paging.meta(page.total()));
    }

    @GetMapping("/financial-decisions/{financialDecisionId}")
    public FinancialDecisionResponse decision(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("financialDecisionId") UUID id) {
        return FinancialDecisionResponse.of(read.decision(PaymentActors.of(principal), id));
    }

    @GetMapping("/refund-authorizations")
    public ReservationListResponses<RefundResponse> refunds(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        var paging = new PageQuery(query, Set.of("page", "limit", "executionState", "overdue", "financialDecisionId"));
        UUID decision = query.containsKey("financialDecisionId") ? uuid(query.get("financialDecisionId")) : null;
        var page = read.refunds(PaymentActors.of(principal), choice(ExecutionState.class, query, "executionState"), flag(query, "overdue"), decision, paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(RefundResponse::of).toList(), paging.meta(page.total()));
    }

    @GetMapping("/refund-authorizations/{refundAuthorizationId}")
    public RefundResponse refund(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("refundAuthorizationId") UUID id) {
        return RefundResponse.of(read.refund(PaymentActors.of(principal), id));
    }

    @GetMapping("/internal/tenants/{tenantId}/financial-decisions")
    public ReservationListResponses<FinancialDecisionResponse> dueDecisions(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @RequestParam Map<String, String> query) {
        requireService(principal);
        var paging = new PageQuery(query, Set.of("page", "limit", "due"));
        requireDue(query);
        var page = read.dueDecisions(tenantId, paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(FinancialDecisionResponse::of).toList(), paging.meta(page.total()));
    }

    @GetMapping("/internal/tenants/{tenantId}/refund-authorizations")
    public ReservationListResponses<RefundResponse> dueRefunds(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @RequestParam Map<String, String> query) {
        requireService(principal);
        var paging = new PageQuery(query, Set.of("page", "limit", "due"));
        requireDue(query);
        var page = read.dueRefunds(tenantId, paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(RefundResponse::of).toList(), paging.meta(page.total()));
    }

    private static <E extends Enum<E>> E choice(Class<E> type, Map<String, String> query, String name) {
        if (!query.containsKey(name)) return null;
        try {
            return Enum.valueOf(type, query.get(name));
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(name, name + " is not a valid value");
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new DomainException("financialDecisionId", "financialDecisionId must be a UUID");
        }
    }
    private static boolean flag(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null) return false;
        if (!"true".equals(value) && !"false".equals(value)) throw new DomainException(name, name + " must be true or false");
        return "true".equals(value);
    }

    private static void requireDue(Map<String, String> query) {
        if (!"true".equals(query.get("due"))) throw new DomainException("due", "due must be true");
    }

    private static void requireService(AuthenticatedPrincipal principal) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
