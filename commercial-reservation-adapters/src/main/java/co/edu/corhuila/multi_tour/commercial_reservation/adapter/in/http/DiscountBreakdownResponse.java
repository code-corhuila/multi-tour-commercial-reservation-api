package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

// Schema DiscountBreakdown of the contract. The quote of a tour has RULE lines only (its core line does not carry the rule id);
// the breakdown of a reservation is its fixed lines (RULE, with the rule id they were created from) and the additional
// discount last (ADDITIONAL).
public record DiscountBreakdownResponse(long baseCents, List<Line> lines, long finalValueCents) {
    public record Line(int order, String kind, String name, String calculationBase, long baseCents, long amountCents,
            long subtotalCents, @JsonInclude(JsonInclude.Include.NON_NULL) String discountRuleId) {}

    static DiscountBreakdownResponse from(DiscountBreakdown breakdown) {
        var lines = breakdown.lines().stream().map(line -> new Line(line.order(), "RULE", line.ruleName(),
                line.calculationBase().name(), line.base().amount(), line.amount().amount(), line.subtotal().amount(), null)).toList();
        return new DiscountBreakdownResponse(breakdown.baseValue().amount(), lines, breakdown.finalValue().amount());
    }

    // The reservation as it was fixed at creation: its RULE lines and its final value, with no additional discount.
    static DiscountBreakdownResponse forFixed(Reservation reservation) {
        var lines = new ArrayList<Line>();
        for (var fixed : reservation.fixedDiscounts()) {
            lines.add(new Line(fixed.order(), "RULE", fixed.ruleName(), fixed.calculationBase().name(), fixed.base().amount(),
                    fixed.amount().amount(), fixed.subtotal().amount(), fixed.ruleId().toString()));
        }
        return new DiscountBreakdownResponse(reservation.projectedValue().amount(), lines, reservation.finalValue().amount());
    }

    // The lines fixed at creation as they were kept, then the additional discount on the last subtotal (RN-04, RN-11).
    // DEFAULT, not in the spec: the additional line is calculated on the PREVIOUS_SUBTOTAL, the only base that fits "a
    // percentage of the last subtotal"; its order follows the fixed lines shown.
    static DiscountBreakdownResponse forReservation(Reservation reservation, DiscountLine additional, String name) {
        var lines = new ArrayList<Line>();
        for (var fixed : reservation.fixedDiscounts()) {
            lines.add(new Line(fixed.order(), "RULE", fixed.ruleName(), fixed.calculationBase().name(), fixed.base().amount(),
                    fixed.amount().amount(), fixed.subtotal().amount(), fixed.ruleId().toString()));
        }
        lines.add(new Line(lines.size() + 1, "ADDITIONAL", name, "PREVIOUS_SUBTOTAL", additional.base().amount(),
                additional.amount().amount(), additional.subtotal().amount(), null));
        return new DiscountBreakdownResponse(reservation.projectedValue().amount(), lines, additional.subtotal().amount());
    }
}
