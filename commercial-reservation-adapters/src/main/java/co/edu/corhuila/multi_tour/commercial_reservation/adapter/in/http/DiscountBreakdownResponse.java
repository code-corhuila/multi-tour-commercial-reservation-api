package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountBreakdown;
import java.util.List;

// Schema DiscountBreakdown of the contract. Every line is a RULE: the additional discount (ADDITIONAL) needs
// a reservation and is not part of this quote. discountRuleId is optional in the contract and the core line does not carry it.
public record DiscountBreakdownResponse(long baseCents, List<Line> lines, long finalValueCents) {
    public record Line(int order, String kind, String name, String calculationBase, long baseCents, long amountCents,
            long subtotalCents) {}

    static DiscountBreakdownResponse from(DiscountBreakdown breakdown) {
        var lines = breakdown.lines().stream().map(line -> new Line(line.order(), "RULE", line.ruleName(),
                line.calculationBase().name(), line.base().amount(), line.amount().amount(), line.subtotal().amount())).toList();
        return new DiscountBreakdownResponse(breakdown.baseValue().amount(), lines, breakdown.finalValue().amount());
    }
}
