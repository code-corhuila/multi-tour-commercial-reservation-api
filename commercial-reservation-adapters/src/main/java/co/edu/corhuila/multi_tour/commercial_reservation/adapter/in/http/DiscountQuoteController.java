package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteTourDiscounts;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Contract quoteCurrentDiscounts (HU-COM-004): read-only, by tour and date; no reservation is involved.
@RestController
@RequestMapping("/api/v1/commercial-reservation/discount-quotes")
public final class DiscountQuoteController {
    private final QuoteTourDiscounts quotes;

    public DiscountQuoteController(QuoteTourDiscounts quotes) {
        this.quotes = quotes;
    }

    @GetMapping
    public DiscountBreakdownResponse quote(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        for (String field : query.keySet()) {
            if (!Set.of("serviceId", "referenceDate", "baseCents").contains(field))
                throw new DomainException(field, "unknown query parameter");
        }
        UUID serviceId;
        LocalDate referenceDate;
        long baseCents;
        try { serviceId = UUID.fromString(required(query, "serviceId")); }
        catch (IllegalArgumentException invalid) { throw new DomainException("serviceId", "must be a UUID"); }
        try { referenceDate = LocalDate.parse(required(query, "referenceDate")); }
        catch (DateTimeParseException invalid) { throw new DomainException("referenceDate", "must be a date (yyyy-MM-dd)"); }
        try { baseCents = Long.parseLong(required(query, "baseCents")); }
        catch (NumberFormatException invalid) { throw new DomainException("baseCents", "must be an integer"); }
        var breakdown = quotes.quote(DiscountRuleHttpAccess.actor(principal), serviceId, referenceDate,
                Cents.of("baseCents", baseCents));
        return DiscountBreakdownResponse.from(breakdown);
    }

    private static String required(Map<String, String> query, String field) {
        String value = query.get(field);
        if (value == null || value.isBlank()) throw new DomainException(field, field + " is required");
        return value;
    }
}
