package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.EvaluateApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class QuoteDiscountBreakdownUseCase implements QuoteDiscountBreakdown {

    private final EvaluateApplicableDiscountRules evaluation;

    public QuoteDiscountBreakdownUseCase(EvaluateApplicableDiscountRules evaluation) {
        this.evaluation = evaluation;
    }

    @Override
    public DiscountBreakdown quote(UUID tenantId, UUID serviceId, LocalDate referenceDate, Cents baseValue) {
        return new DiscountBreakdown(baseValue, List.of(), baseValue, new Cents(0), Set.of());
    }
}
