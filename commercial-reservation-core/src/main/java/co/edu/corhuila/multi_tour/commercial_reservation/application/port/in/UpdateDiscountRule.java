package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRulePatch;
import java.util.UUID;

public interface UpdateDiscountRule {
    DiscountRule update(DiscountRuleActor actor, UUID ruleId, DiscountRulePatch patch);
}
