package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleChanged;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// Rules and events share one fake store, so a rollback of the transaction undoes both (norm 5.3.11).
final class DiscountRuleFakes implements DiscountRuleRepository, DiscountRuleChangeOutbox, DiscountRuleTransaction {
    static final UUID TENANT = new UUID(0, 0xb1), OTHER_TENANT = new UUID(0, 0xb2), ACTOR = new UUID(0, 0xc1);
    static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    final Map<String, DiscountRule> rules = new HashMap<>();
    final List<DiscountRuleChanged> events = new ArrayList<>();
    RuntimeException failOnSave, failOnAppend;

    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final IdGenerator ids = () -> new UUID(0, 0xa1);

    static DiscountRuleActor actor(DiscountRuleActor.Role role) { return new DiscountRuleActor(TENANT, ACTOR, role); }

    @Override public void save(DiscountRule rule) {
        if (failOnSave != null) throw failOnSave;
        rules.put(rule.tenantId() + "/" + rule.ruleId(), rule);
    }
    @Override public Optional<DiscountRule> findByTenantAndId(UUID tenantId, UUID ruleId) {
        return Optional.ofNullable(rules.get(tenantId + "/" + ruleId));
    }
    @Override public List<DiscountRule> findByTenant(UUID tenantId) {
        return rules.values().stream().filter(r -> r.tenantId().equals(tenantId)).toList();
    }
    @Override public void append(DiscountRuleChanged event) {
        if (failOnAppend != null) throw failOnAppend;
        events.add(event);
    }
    @Override public <T> T inTransaction(Supplier<T> work) {
        var rulesBefore = new HashMap<>(rules);
        var eventsBefore = new ArrayList<>(events);
        try {
            return work.get();
        } catch (RuntimeException e) {
            rules.clear(); rules.putAll(rulesBefore);
            events.clear(); events.addAll(eventsBefore);
            throw e;
        }
    }
}
