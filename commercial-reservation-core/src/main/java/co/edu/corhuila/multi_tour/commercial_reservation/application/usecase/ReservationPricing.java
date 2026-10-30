package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.BreakdownLine;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.EvaluateApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TransportRateReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FixedDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The one function behind a quote and a creation (plan D-03): the tour and the single transport line make the projected
// value, and the discounts come from HU-COM-004 through its inbound port. Nothing about percentages lives here.
// The rules are read once more to copy their type, value and cap into the lines that the reservation will fix (RN-04).
public class ReservationPricing {

    private final QuoteDiscountBreakdown discounts;
    private final EvaluateApplicableDiscountRules rulesInForce;
    private final TransportRateReader transport;

    public ReservationPricing(QuoteDiscountBreakdown discounts, EvaluateApplicableDiscountRules rulesInForce,
            TransportRateReader transport) {
        this.discounts = discounts;
        this.rulesInForce = rulesInForce;
        this.transport = transport;
    }

    public ReservationQuote quote(UUID tenantId, CatalogService tour, UUID departureId, int people, LocalDate date) {
        if (people < 1) throw new DomainException("people", "people must be at least 1");
        // AC-07.6: a catalog item of type transport is never a reserved service; the transport is a line of the tour.
        if (tour.serviceType() != CatalogServiceType.TOUR) {
            throw new BusinessRuleViolation("only a tour can be reserved as a service");
        }
        if (tour.catalogStatus() != CatalogStatus.ACTIVE) throw new BusinessRuleViolation("the tour is inactive");
        ReservedService service = new ReservedService(tour.serviceId(), tour.terms().baseRate(), transportRateOf(tenantId, tour, departureId));
        Cents projected = service.projectedFor(people);
        DiscountBreakdown breakdown = discounts.quote(tenantId, tour.serviceId(), date, projected);
        return new ReservationQuote(service, people, service.tourLine(people), service.transportLine(people), projected,
                fixedLines(tenantId, tour.serviceId(), date, breakdown), breakdown.finalValue());
    }

    // RN-15: a tour that requires transport and has no fare is refused instead of guessing one.
    private Cents transportRateOf(UUID tenantId, CatalogService tour, UUID departureId) {
        if (!tour.terms().transportRequired()) return null;
        return transport.perPersonCents(tenantId, tour.serviceId(), departureId)
                .orElseThrow(() -> new BusinessRuleViolation("the tour requires transport and has no per-person fare"));
    }

    private List<FixedDiscount> fixedLines(UUID tenantId, UUID serviceId, LocalDate date, DiscountBreakdown breakdown) {
        ApplicableDiscountRules applicable = rulesInForce.evaluate(tenantId, serviceId, date);
        Map<UUID, DiscountRule> byId = new HashMap<>();
        applicable.rules().forEach(rule -> byId.put(rule.ruleId(), rule));
        List<FixedDiscount> lines = new ArrayList<>();
        for (BreakdownLine line : breakdown.lines()) {
            DiscountRule rule = byId.get(line.ruleId());
            if (rule == null) throw new BusinessRuleViolation("a discount rule changed while the value was quoted");
            DiscountType type = rule.percentageBasisPoints() != null ? DiscountType.PERCENTAGE : DiscountType.FIXED_AMOUNT;
            lines.add(new FixedDiscount(line.order(), line.ruleId(), line.ruleName(), type, rule.percentageBasisPoints(),
                    rule.amountCents(), line.calculationBase(), line.base(), line.amount(), line.subtotal(), rule.capCents()));
        }
        return lines;
    }
}
