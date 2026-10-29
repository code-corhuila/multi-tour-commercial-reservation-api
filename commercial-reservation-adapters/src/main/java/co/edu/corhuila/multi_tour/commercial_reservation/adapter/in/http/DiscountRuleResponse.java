package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

// tenantId is never exposed. Exactly one of the two values is present; capCents only when set.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiscountRuleResponse(UUID discountRuleId, String name, String validFrom, String validTo, int priority,
        DiscountBase calculationBase, Integer percentageBasisPoints, Long amountCents, String serviceCoverage,
        List<UUID> serviceIds, Long capCents, DiscountStatus ruleStatus, String createdAt, String updatedAt) {
    public static DiscountRuleResponse from(DiscountRule rule) {
        var coverage = rule.serviceCoverage();
        return new DiscountRuleResponse(rule.ruleId(), rule.name(), rule.validFrom().toString(),
                rule.validTo().toString(), rule.priority(), rule.calculationBase(), rule.percentageBasisPoints(),
                rule.amountCents(), coverage.isAll() ? "ALL" : "SELECTED",
                coverage.serviceIds().stream().sorted().toList(), rule.capCents(), rule.status(),
                rule.createdAt().toString(), rule.updatedAt().toString());
    }
}
