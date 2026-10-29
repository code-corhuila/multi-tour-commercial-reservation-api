package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTour;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

// The one place a catalog item becomes a public tour (plan D-02, D-06). Nothing else reads the item for the browser.
final class PublicTourProjection {

    private static final long BASIS_POINTS = 10_000L;

    private final QuoteDiscountBreakdown quote;
    private final Clock clock;

    PublicTourProjection(QuoteDiscountBreakdown quote, Clock clock) {
        this.quote = quote;
        this.clock = clock;
    }

    // Belt and braces over the query: tenant, status and kind are re-checked on every row (RN-01, RN-03, RN-09).
    static boolean isOffered(CatalogService service, UUID tenantId) {
        return service.tenantId().equals(tenantId) && service.catalogStatus() == CatalogStatus.ACTIVE
                && service.serviceType() == CatalogServiceType.TOUR;
    }

    PublicTour project(CatalogService service) {
        // The tenant time zone has no source yet (DP-03, G-H): the UTC date of the clock is the PROPOSED interim.
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Cents base = service.terms().baseRate();
        DiscountBreakdown breakdown = quote.quote(service.tenantId(), service.serviceId(), today, base);
        Integer percentage = breakdown.lines().isEmpty() || base.amount() == 0 ? null
                : (int) (Math.multiplyExact(breakdown.totalDiscounted().amount(), BASIS_POINTS) / base.amount());
        Integer capacity = service.terms().configuredCapacity();
        return new PublicTour(service.serviceId(), service.commercialName(), service.terms().transportRequired(),
                base.amount(), breakdown.finalValue().amount(), percentage, capacity == null ? 0 : capacity);
    }
}
