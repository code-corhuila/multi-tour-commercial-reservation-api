package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import java.util.UUID;

public interface GetDiscountRule {
    DiscountRule get(DiscountRuleActor actor, UUID ruleId);
}
