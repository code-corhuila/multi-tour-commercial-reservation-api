package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleChanged;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleState;
import java.time.Clock;
import java.time.Instant;

public class CreateDiscountRuleUseCase implements CreateDiscountRule {

    private final DiscountRuleRepository repository;
    private final DiscountRuleChangeOutbox outbox;
    private final DiscountRuleTransaction transaction;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateDiscountRuleUseCase(DiscountRuleRepository repository, DiscountRuleChangeOutbox outbox,
            DiscountRuleTransaction transaction, IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.transaction = transaction;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public DiscountRule create(DiscountRuleActor actor, Command command) {
        DiscountRuleAuthorization.requireWrite(actor);
        Instant now = clock.instant();
        DiscountRule rule = DiscountRule.create(ids.newId(), actor.tenantId(), command.name(), command.validFrom(),
                command.validTo(), command.priority(), command.calculationBase(), command.percentageBasisPoints(),
                command.amountCents(), now);
        // The rule and its event commit or roll back together (plan D-10, norm 5.3.11).
        return transaction.inTransaction(() -> {
            repository.save(rule);
            outbox.append(new DiscountRuleChanged(rule.tenantId(), rule.ruleId(), null, DiscountRuleState.of(rule),
                    actor.actorId(), now));
            return rule;
        });
    }
}
