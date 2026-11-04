package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReactivateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleChanged;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleState;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// AC-03.17: the mirror of the inactivation. The reactivated rule only reaches reservations created afterwards (AC-03.11).
public class ReactivateDiscountRuleUseCase implements ReactivateDiscountRule {

    private final DiscountRuleRepository repository;
    private final DiscountRuleChangeOutbox outbox;
    private final DiscountRuleTransaction transaction;
    private final Clock clock;

    public ReactivateDiscountRuleUseCase(DiscountRuleRepository repository, DiscountRuleChangeOutbox outbox,
            DiscountRuleTransaction transaction, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public DiscountRule reactivate(DiscountRuleActor actor, UUID ruleId) {
        DiscountRuleAuthorization.requireWrite(actor);
        return transaction.inTransaction(() -> {
            DiscountRule rule = repository.findByTenantAndId(actor.tenantId(), ruleId)
                    .orElseThrow(() -> new DiscountRuleNotFoundException(ruleId));
            if (rule.isActive()) return rule; // a repeated reactivation is not a new fact: no write, no event
            DiscountRuleState previous = DiscountRuleState.of(rule);
            Instant now = clock.instant();
            rule.reactivate(actor.actorId(), now);
            repository.save(rule);
            outbox.append(new DiscountRuleChanged(rule.tenantId(), rule.ruleId(), previous, DiscountRuleState.of(rule),
                    actor.actorId(), now));
            return rule;
        });
    }
}
