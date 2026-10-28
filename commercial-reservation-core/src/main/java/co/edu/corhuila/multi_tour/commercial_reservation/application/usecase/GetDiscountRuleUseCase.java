package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import java.util.UUID;

public class GetDiscountRuleUseCase implements GetDiscountRule {

    private final DiscountRuleRepository repository;

    public GetDiscountRuleUseCase(DiscountRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public DiscountRule get(DiscountRuleActor actor, UUID ruleId) {
        DiscountRuleAuthorization.requireRead(actor);
        // Another tenant's rule is answered as a missing one (RN-12).
        return repository.findByTenantAndId(actor.tenantId(), ruleId)
                .orElseThrow(() -> new DiscountRuleNotFoundException(ruleId));
    }
}
