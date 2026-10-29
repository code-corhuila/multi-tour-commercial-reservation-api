package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

// The date of the discounts is the clock of the service in its zone, never a date sent by the caller (RN-13).
public class QuoteReservationUseCase implements QuoteReservation {

    private final CatalogServiceRepository catalog;
    private final ReservationPricing pricing;
    private final Clock clock;

    public QuoteReservationUseCase(CatalogServiceRepository catalog, ReservationPricing pricing, Clock clock) {
        this.catalog = catalog;
        this.pricing = pricing;
        this.clock = clock;
    }

    @Override
    public ReservationQuote quote(UUID tenantId, UUID serviceId, UUID departureId, int people) {
        return pricing.quote(tenantId, CatalogLookup.require(catalog, tenantId, serviceId), departureId, people,
                LocalDate.now(clock));
    }
}
