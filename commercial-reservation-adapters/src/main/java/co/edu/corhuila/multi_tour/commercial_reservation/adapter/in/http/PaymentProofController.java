package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ListProofsQuery;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPaymentProofs;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadPaymentProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadPaymentProofs;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The review of the payment proofs, reads only (PROPOSED in the contract): the list with the 24 hour mark, one proof, the file through
// the API with no-store (ADR-005 E6) and the overdue list of job 2, which takes the tenant from its path and accepts a service token
// only. The role gate runs first (PaymentProofHttpAccess); the file is judged by its own use case.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class PaymentProofController {
    private final ListPaymentProofs list;
    private final ReadPaymentProofs read;
    private final ReadPaymentProofFile file;
    private final CustomerRepository customers;

    public PaymentProofController(ListPaymentProofs list, ReadPaymentProofs read, ReadPaymentProofFile file, CustomerRepository customers) {
        this.list = list;
        this.read = read;
        this.file = file;
        this.customers = customers;
    }

    @GetMapping("/payment-proofs")
    public PaymentProofPageResponse list(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        var paging = new PageQuery(query, Set.of("page", "limit", "status", "overdue"));
        PaymentActor actor = PaymentActors.of(principal);
        boolean overdue = query.containsKey("overdue") && flag(query.get("overdue"));
        ValidationStatus status = null;
        if (query.containsKey("status")) {
            try {
                status = ValidationStatus.valueOf(query.get("status"));
            } catch (IllegalArgumentException invalid) {
                throw new DomainException("status", "status must be IN_VALIDATION, APPROVED or REJECTED");
            }
        }
        return page(actor.tenantId(), list.list(new ListProofsQuery(actor, status, overdue, paging.page, paging.limit)), paging);
    }

    @GetMapping("/payment-proofs/{paymentId}")
    public ProofResponse get(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("paymentId") UUID paymentId) {
        PaymentActor actor = PaymentActors.of(principal);
        ProofView view = read.get(actor, paymentId);
        return ProofResponse.of(view, names(actor.tenantId()).apply(view.customerId()));
    }

    @GetMapping("/payment-proofs/{paymentId}/file")
    public ResponseEntity<byte[]> download(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("paymentId") UUID paymentId) {
        ProofFile proof = file.read(PaymentActors.of(principal), paymentId);
        // DP-14-02 (PROPOSED): a download the browser must not sniff or render.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(proof.contentType()))
                .header("Content-Disposition", "attachment; filename=receipt-" + paymentId + "." + extension(proof.contentType()))
                .header("X-Content-Type-Options", "nosniff").body(proof.bytes());
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "image/jpeg" -> "jpg";
            default -> "png";
        };
    }

    @GetMapping("/internal/tenants/{tenantId}/payment-proofs")
    public PaymentProofPageResponse overdue(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @RequestParam Map<String, String> query) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var paging = new PageQuery(query, Set.of("page", "limit", "overdue"));
        if (!query.containsKey("overdue") || !flag(query.get("overdue"))) throw new DomainException("overdue", "overdue must be true");
        return page(tenantId, list.overdueOf(tenantId, paging.page, paging.limit), paging);
    }

    private PaymentProofPageResponse page(UUID tenantId, ProofPage found, PageQuery paging) {
        var name = names(tenantId);
        return new PaymentProofPageResponse(found.items().stream().map(view -> ProofResponse.of(view, name.apply(view.customerId()))).toList(),
                paging.meta(found.total()), found.overdueCount());
    }

    // The display name is read from the customer record once per customer and never logged.
    private java.util.function.Function<UUID, String> names(UUID tenantId) {
        Map<UUID, String> known = new HashMap<>();
        return customerId -> known.computeIfAbsent(customerId,
                id -> customers.findByTenantAndId(tenantId, id).map(Customer::fullName).orElse(""));
    }

    private static boolean flag(String value) {
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new DomainException("overdue", "overdue must be true or false");
    }
}
