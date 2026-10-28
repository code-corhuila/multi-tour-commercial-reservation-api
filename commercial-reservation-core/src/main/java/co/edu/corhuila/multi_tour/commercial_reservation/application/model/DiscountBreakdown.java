package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.List;
import java.util.Set;

// AC-04.1 to AC-04.3: the base value, the lines in priority order, the final value (never below zero) and the total discounted.
// tiedPriorities is reported, not resolved (DP-07); accumulation (HU-COM-003 DP-04) is not modelled.
public record DiscountBreakdown(Cents baseValue, List<BreakdownLine> lines, Cents finalValue, Cents totalDiscounted,
        Set<Integer> tiedPriorities) {

    public DiscountBreakdown {
        lines = List.copyOf(lines);
        tiedPriorities = Set.copyOf(tiedPriorities);
    }
}
