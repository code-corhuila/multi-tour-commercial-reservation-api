package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.BreakdownLine;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.EvaluateApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BasisPoints;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Chains the applicable rules by priority, each on its declared base (RN-02). Accumulation is not modelled (HU-COM-003 DP-04):
// every rule simply takes its amount off the running subtotal. The reference date is the caller's only "now" (DP-04).
public class QuoteDiscountBreakdownUseCase implements QuoteDiscountBreakdown {

    private final EvaluateApplicableDiscountRules evaluation;

    public QuoteDiscountBreakdownUseCase(EvaluateApplicableDiscountRules evaluation) {
        this.evaluation = evaluation;
    }

    @Override
    public DiscountBreakdown quote(UUID tenantId, UUID serviceId, LocalDate referenceDate, Cents baseValue) {
        ApplicableDiscountRules applicable = evaluation.evaluate(tenantId, serviceId, referenceDate);
        List<BreakdownLine> lines = new ArrayList<>();
        long subtotal = baseValue.amount();
        for (DiscountRule rule : applicable.rules()) {
            Cents base = rule.calculationBase() == DiscountBase.ORIGINAL_VALUE ? baseValue : new Cents(subtotal);
            // Never above the rule's own cap and never below zero for the running subtotal.
            long amount = Math.min(Math.min(rawAmount(rule, base), capOf(rule)), subtotal);
            subtotal -= amount;
            lines.add(new BreakdownLine(lines.size() + 1, rule.name(), rule.calculationBase(), base, new Cents(amount), new Cents(subtotal)));
        }
        return new DiscountBreakdown(baseValue, lines, new Cents(subtotal), new Cents(baseValue.amount() - subtotal),
                applicable.tiedPriorities());
    }

    private static long rawAmount(DiscountRule rule, Cents base) {
        DiscountLine line = rule.percentageBasisPoints() != null
                ? DiscountLine.percentage(base, new BasisPoints(rule.percentageBasisPoints()))
                : DiscountLine.fixedAmount(base, new Cents(rule.amountCents()));
        return line.amount().amount();
    }

    private static long capOf(DiscountRule rule) {
        return rule.capCents() == null ? Long.MAX_VALUE : rule.capCents();
    }
}
