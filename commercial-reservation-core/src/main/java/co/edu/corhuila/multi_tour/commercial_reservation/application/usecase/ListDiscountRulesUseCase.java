package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import java.util.Comparator;
import java.util.List;

public class ListDiscountRulesUseCase implements ListDiscountRules {

    private final DiscountRuleRepository repository;

    public ListDiscountRulesUseCase(DiscountRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DiscountRule> list(DiscountRuleActor actor) {
        DiscountRuleAuthorization.requireRead(actor);
        // Same order for every store: newest first, the identifier breaks a tie so pages never overlap.
        return repository.findByTenant(actor.tenantId()).stream()
                .sorted(Comparator.comparing(DiscountRule::createdAt).thenComparing(DiscountRule::ruleId).reversed())
                .toList();
    }
}
