package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadSensitiveCustomerData;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
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

// Nothing deletes nor merges a person (spec section 6). The three routes are PROPOSED in the contract (Customers).
@RestController
@RequestMapping("/api/v1/commercial-reservation/customers")
public final class CustomerController {
    private static final String BASE = "/api/v1/commercial-reservation/customers/";
    private static final int REASON_MAX = 500;
    private final IdempotentCustomerRegistration registration;
    private final GetCustomer get;
    private final ReadSensitiveCustomerData sensitive;

    public CustomerController(IdempotentCustomerRegistration registration, GetCustomer get,
            ReadSensitiveCustomerData sensitive) {
        this.registration = registration;
        this.get = get;
        this.sensitive = sensitive;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> register(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody RegisterCustomerRequest request) {
        if (key == null || key.length() < 8 || key.length() > 128) {
            throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        }
        IdempotentCustomerRegistration.Result result;
        try {
            result = registration.register(CustomerHttpAccess.actor(principal), request.toCommand(), key);
        } catch (CatalogServiceNotFoundException missing) {
            // A service of another tenant looks missing too (RN-12), so the answer names the field only.
            throw new DomainException("serviceId", "serviceId does not exist");
        }
        var body = CustomerResponse.from(result.customer());
        return result.created() ? ResponseEntity.created(URI.create(BASE + body.customerId())).body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse read(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("customerId") UUID customerId) {
        return CustomerResponse.from(get.get(CustomerHttpAccess.actor(principal), customerId));
    }

    @GetMapping("/{customerId}/sensitive-data")
    public ResponseEntity<CustomerSensitiveDataResponse> readSensitive(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("customerId") UUID customerId,
            @RequestParam(value = "reason", required = false) String reason) {
        if (reason != null && reason.length() > REASON_MAX) {
            throw new DomainException("reason", "must have at most " + REASON_MAX + " characters");
        }
        // The use case audits before it answers; a customer with no risk data on file has nothing to return.
        var data = sensitive.read(CustomerHttpAccess.actor(principal), customerId, reason)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CustomerSensitiveDataResponse.from(customerId, data));
    }
}
