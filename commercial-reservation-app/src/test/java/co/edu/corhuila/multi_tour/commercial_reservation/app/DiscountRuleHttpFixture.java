package co.edu.corhuila.multi_tour.commercial_reservation.app;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the real JDBC adapter belongs to the persistence PR of this story.
@TestConfiguration
class DiscountRuleHttpFixture {
    @Bean @Primary MemoryDiscountRules memoryDiscountRules() { return new MemoryDiscountRules(); }

    static final class MemoryDiscountRules
            implements DiscountRuleRepository, DiscountRuleChangeOutbox, DiscountRuleTransaction {
        final Map<UUID, DiscountRule> rules = new HashMap<>();
        final List<DiscountRuleChanged> events = new ArrayList<>();
        public void save(DiscountRule rule) { rules.put(rule.ruleId(), rule); }
        public Optional<DiscountRule> findByTenantAndId(UUID tenant, UUID id) {
            return Optional.ofNullable(rules.get(id)).filter(rule -> rule.tenantId().equals(tenant));
        }
        public void append(DiscountRuleChanged event) { events.add(event); }
        public <T> T inTransaction(Supplier<T> work) { return work.get(); }
    }
}
