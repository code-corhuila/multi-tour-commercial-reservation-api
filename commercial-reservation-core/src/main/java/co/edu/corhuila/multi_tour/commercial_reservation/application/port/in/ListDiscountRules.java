package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import java.util.List;

public interface ListDiscountRules {
    // The rules of the actor's tenant, inactive ones included, newest first.
    List<DiscountRule> list(DiscountRuleActor actor);
}
