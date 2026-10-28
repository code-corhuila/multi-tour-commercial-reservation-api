package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.EvaluateApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import java.time.LocalDate;
import java.util.UUID;

public class EvaluateApplicableDiscountRulesUseCase implements EvaluateApplicableDiscountRules {

    private final DiscountRuleRepository repository;

    public EvaluateApplicableDiscountRulesUseCase(DiscountRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public ApplicableDiscountRules evaluate(UUID tenantId, UUID serviceId, LocalDate referenceDate) {
        return new ApplicableDiscountRules(java.util.List.of(), java.util.Set.of());
    }
}
