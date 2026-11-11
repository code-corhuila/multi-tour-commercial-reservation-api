package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

// The date of the discounts is the clock of the service in its zone, never a date sent by the caller (RN-13).
// With a tenant directory, an inactive tenant quotes nothing (AC-07.20), like it creates nothing.
public class QuoteReservationUseCase implements QuoteReservation {

    private final CatalogServiceRepository catalog;
    private final ReservationPricing pricing;
    private final ReservableDeparture departures;
    private final Clock clock;
    private final ActiveTenant activeTenant; // null when no directory is wired: the tenant is not checked

    public QuoteReservationUseCase(CatalogServiceRepository catalog, ReservationPricing pricing, ReservableDeparture departures,
            Clock clock) {
        this(catalog, pricing, departures, clock, null);
    }

    public QuoteReservationUseCase(CatalogServiceRepository catalog, ReservationPricing pricing, ReservableDeparture departures,
            Clock clock, TenantDirectory tenants) {
        this.catalog = catalog;
        this.pricing = pricing;
        this.departures = departures;
        this.clock = clock;
        this.activeTenant = tenants == null ? null : new ActiveTenant(tenants);
    }

    @Override
    public ReservationQuote quote(UUID tenantId, UUID serviceId, UUID departureId, int people) {
        if (activeTenant != null) activeTenant.require(tenantId);
        var tour = CatalogLookup.require(catalog, tenantId, serviceId);
        departures.requireOpen(tenantId, departureId, serviceId);
        return pricing.quote(tenantId, tour, departureId, people, LocalDate.now(clock));
    }
}
