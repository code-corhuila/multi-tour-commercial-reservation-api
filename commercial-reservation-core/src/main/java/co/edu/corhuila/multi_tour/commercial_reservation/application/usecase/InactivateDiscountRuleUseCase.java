package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.InactivateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import java.time.Clock;
import java.util.UUID;

public class InactivateDiscountRuleUseCase implements InactivateDiscountRule {

    public InactivateDiscountRuleUseCase(DiscountRuleRepository repository, DiscountRuleChangeOutbox outbox,
            DiscountRuleTransaction transaction, Clock clock) {
    }

    @Override
    public DiscountRule inactivate(DiscountRuleActor actor, UUID ruleId) {
        return null;
    }
}
