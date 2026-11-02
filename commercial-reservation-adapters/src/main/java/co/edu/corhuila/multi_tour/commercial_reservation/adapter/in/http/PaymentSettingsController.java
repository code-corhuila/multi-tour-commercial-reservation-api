package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentQuoteQuery;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetOwnReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPaymentQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManagePaymentParameters;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangePaymentParametersCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The quote of what a reservation can pay and the payment parameters of the tenant (PROPOSED in the contract). The service computes
// the quote and never takes an amount from the caller (AC-09.15); the role gate runs first (PaymentHttpAccess).
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class PaymentSettingsController {
    private final GetPaymentQuote quote;
    private final GetOwnReservation getOwn;
    private final ManagePaymentParameters parameters;

    public PaymentSettingsController(GetPaymentQuote quote, GetOwnReservation getOwn, ManagePaymentParameters parameters) {
        this.quote = quote;
        this.getOwn = getOwn;
        this.parameters = parameters;
    }

    @GetMapping("/reservations/{reservationId}/payment-quote")
    public PaymentQuoteResponse quote(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestParam Map<String, String> query) {
        for (String field : query.keySet()) {
            if (!Set.of("type").contains(field)) throw new DomainException(field, "unknown query parameter");
        }
        PaymentActor actor = PaymentActors.of(principal);
        PaymentType type = null;
        if (query.containsKey("type")) {
            try {
                type = PaymentType.valueOf(query.get("type"));
            } catch (IllegalArgumentException invalid) {
                throw new DomainException("type", "type must be FULL_PAYMENT or DEPOSIT");
            }
        }
        if (actor.role() == PaymentActor.Role.FINAL_CUSTOMER) getOwn.get(actor.tenantId(), actor.actorId(), reservationId);
        return PaymentQuoteResponse.of(quote.quote(new PaymentQuoteQuery(actor.tenantId(), reservationId, type)));
    }

    @GetMapping("/payment-parameters")
    public ReservationListResponses<PaymentParameterResponse> list(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @RequestParam Map<String, String> query) {
        var paging = new PageQuery(query, Set.of("page", "limit"));
        List<PaymentParameter> rows = parameters.read(PaymentActors.of(principal));
        var data = rows.stream().skip((paging.page - 1L) * paging.limit).limit(paging.limit).map(PaymentParameterResponse::of).toList();
        return new ReservationListResponses<>(data, paging.meta(rows.size()));
    }

    @PutMapping("/payment-parameters")
    public Map<String, List<PaymentParameterResponse>> change(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestBody ChangePaymentParametersRequest request) {
        PaymentActor actor = PaymentActors.of(principal);
        var changed = parameters.change(new ChangePaymentParametersCommand(actor, request.reason(), request.values()));
        return Map.of("data", changed.stream().map(PaymentParameterResponse::of).toList());
    }
}
