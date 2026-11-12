package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditSummary;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadCreditBalances;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The reads of HU-COM-017 (PROPOSED in the contract, AC-17.9, AC-17.13, AC-17.16): the list, the detail with its history and the summary. CreditHttpAccess runs
// first; a Final Customer reads only their own credit and the use case makes that a condition of the query. A query parameter the contract does not name is a 400.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class CreditReadController {
    private final ReadCreditBalances read;

    public CreditReadController(ReadCreditBalances read) {
        this.read = read;
    }

    @GetMapping("/credit-balances")
    public ReservationListResponses<CreditBalanceResponse> list(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        var paging = new PageQuery(query, Set.of("page", "limit", "customerId", "available"));
        UUID customer = query.containsKey("customerId") ? uuid(query.get("customerId")) : null;
        var page = read.page(PaymentActors.of(principal), customer, flag(query, "available"), paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(CreditBalanceResponse::of).toList(), paging.meta(page.total()));
    }

    @GetMapping("/credit-balances/{creditBalanceId}")
    public CreditBalanceDetailResponse detail(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("creditBalanceId") UUID id) {
        return CreditBalanceDetailResponse.of(read.detail(PaymentActors.of(principal), id));
    }

    @GetMapping("/credit-balance-summary")
    public CreditSummary summary(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal) {
        return read.summary(PaymentActors.of(principal));
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new DomainException("customerId", "customerId must be a UUID");
        }
    }

    private static boolean flag(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null) return false;
        if (!"true".equals(value) && !"false".equals(value)) throw new DomainException(name, name + " must be true or false");
        return "true".equals(value);
    }
}
