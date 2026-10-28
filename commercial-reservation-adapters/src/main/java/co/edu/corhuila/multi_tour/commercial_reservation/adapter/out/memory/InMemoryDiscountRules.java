package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRuleChanged;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// TEMPORARY and volatile: stands in until the JDBC adapter of HU-COM-003 replaces it. Nothing survives a restart,
// and events are only kept in memory, so no environment may rely on it. One monitor is the transaction boundary.
public final class InMemoryDiscountRules implements DiscountRuleRepository, DiscountRuleChangeOutbox, DiscountRuleTransaction {
    private final Map<UUID, DiscountRule> rows = new HashMap<>();
    private final List<DiscountRuleChanged> outbox = new ArrayList<>();

    @Override public synchronized void save(DiscountRule rule) { rows.put(rule.ruleId(), rule); }

    @Override public synchronized Optional<DiscountRule> findByTenantAndId(UUID tenantId, UUID ruleId) {
        return Optional.ofNullable(rows.get(ruleId)).filter(rule -> rule.tenantId().equals(tenantId));
    }

    @Override public synchronized void append(DiscountRuleChanged event) { outbox.add(event); }

    @Override public synchronized <T> T inTransaction(Supplier<T> work) { return work.get(); }
}
