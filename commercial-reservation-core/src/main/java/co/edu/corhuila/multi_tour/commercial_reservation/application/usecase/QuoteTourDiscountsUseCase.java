package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteTourDiscounts;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.LocalDate;
import java.util.UUID;

public class QuoteTourDiscountsUseCase implements QuoteTourDiscounts {

    private final CatalogServiceRepository catalog;
    private final QuoteDiscountBreakdown breakdown;

    public QuoteTourDiscountsUseCase(CatalogServiceRepository catalog, QuoteDiscountBreakdown breakdown) {
        this.catalog = catalog;
        this.breakdown = breakdown;
    }

    @Override
    public DiscountBreakdown quote(DiscountRuleActor actor, UUID serviceId, LocalDate referenceDate, Cents baseValue) {
        DiscountRuleAuthorization.requireQuote(actor);
        // A tour of another tenant is answered as a missing one (AC-04.10).
        catalog.findByTenantAndId(actor.tenantId(), serviceId)
                .orElseThrow(() -> new CatalogServiceNotFoundException(serviceId));
        return breakdown.quote(actor.tenantId(), serviceId, referenceDate, baseValue);
    }
}
