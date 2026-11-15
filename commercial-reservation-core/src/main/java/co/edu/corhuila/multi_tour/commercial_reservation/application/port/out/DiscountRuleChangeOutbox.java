package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleChanged;

// The only thing besides the rule itself that this slice writes (RN-10, RN-11).
public interface DiscountRuleChangeOutbox {
    void append(DiscountRuleChanged event);
}
