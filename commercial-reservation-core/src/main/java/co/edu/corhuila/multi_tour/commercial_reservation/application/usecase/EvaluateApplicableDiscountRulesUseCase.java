package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.EvaluateApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Reads no clock: the caller's reference date is the only "now" (plan D-04, DP-02).
// Lower priority number first; equal priorities are accepted and ordered by creation time then id (DP-08, RNF-03).
public class EvaluateApplicableDiscountRulesUseCase implements EvaluateApplicableDiscountRules {

    private static final Comparator<DiscountRule> ORDER = Comparator.comparing(DiscountRule::priority)
            .thenComparing(DiscountRule::createdAt).thenComparing(DiscountRule::ruleId);

    private final DiscountRuleRepository repository;

    public EvaluateApplicableDiscountRulesUseCase(DiscountRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public ApplicableDiscountRules evaluate(UUID tenantId, UUID serviceId, LocalDate referenceDate) {
        List<DiscountRule> applicable = repository.findByTenant(tenantId).stream()
                .filter(r -> r.tenantId().equals(tenantId))
                .filter(DiscountRule::isActive)
                .filter(r -> !referenceDate.isBefore(r.validFrom()) && !referenceDate.isAfter(r.validTo()))
                // AC-03.10: created after the reference date (UTC day) does not apply to what was fixed then.
                .filter(r -> !r.createdAt().atZone(ZoneOffset.UTC).toLocalDate().isAfter(referenceDate))
                .filter(r -> r.serviceCoverage().isAll() || r.serviceCoverage().serviceIds().contains(serviceId))
                .sorted(ORDER)
                .toList();
        Set<Integer> tied = applicable.stream()
                .collect(Collectors.groupingBy(DiscountRule::priority, Collectors.counting())).entrySet().stream()
                .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());
        return new ApplicableDiscountRules(applicable, tied);
    }
}
