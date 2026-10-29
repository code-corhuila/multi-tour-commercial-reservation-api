package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// No deletion on purpose: a rule leaves service by inactivation (RN-13).
public interface DiscountRuleRepository {
    void save(DiscountRule rule);
    Optional<DiscountRule> findByTenantAndId(UUID tenantId, UUID ruleId);
    List<DiscountRule> findByTenant(UUID tenantId);
}
