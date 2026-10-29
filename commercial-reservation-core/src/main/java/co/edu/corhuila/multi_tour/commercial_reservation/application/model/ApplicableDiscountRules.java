package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import java.util.List;
import java.util.Set;

// tiedPriorities reports a priority held by more than one rule: DP-08 is open, so a tie is shown, not resolved.
public record ApplicableDiscountRules(List<DiscountRule> rules, Set<Integer> tiedPriorities) {

    public ApplicableDiscountRules {
        rules = List.copyOf(rules);
        tiedPriorities = Set.copyOf(tiedPriorities);
    }
}
