package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FixedDiscount;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

// Every figure is an integer in cents. The transport is one line (null, so absent, when the tour has none); the discounts keep the
// rules whose amount came out zero (AC-07.10). The lines are shared with the reservation answer.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationQuoteResponse(Line tourLine, Line transportLine, long projectedValueCents,
        List<DiscountLine> discounts, long finalValueCents) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(long ratePerPersonCents, int people, long amountCents) {
        static Line of(Cents ratePerPerson, int people, Cents amount) {
            return amount == null ? null : new Line(ratePerPerson.amount(), people, amount.amount());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DiscountLine(int order, UUID discountRuleId, String name, String calculationBase, long baseCents,
            long amountCents, Long capCents, long subtotalCents) {
        static DiscountLine of(FixedDiscount line) {
            return new DiscountLine(line.order(), line.ruleId(), line.ruleName(), line.calculationBase().name(),
                    line.base().amount(), line.amount().amount(), line.capCents(), line.subtotal().amount());
        }
    }

    static List<DiscountLine> lines(List<FixedDiscount> discounts) {
        return discounts.stream().map(DiscountLine::of).toList();
    }

    public static ReservationQuoteResponse from(ReservationQuote quote) {
        var service = quote.service();
        return new ReservationQuoteResponse(Line.of(service.baseRatePerPerson(), quote.people(), quote.tourLine()),
                service.transportRatePerPerson() == null ? null
                        : Line.of(service.transportRatePerPerson(), quote.people(), quote.transportLine()),
                quote.projectedValue().amount(), lines(quote.discounts()), quote.finalValue().amount());
    }
}
