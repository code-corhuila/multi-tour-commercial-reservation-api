package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PublicOfferController;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPublicTour;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPublicTours;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetPublicTourUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListPublicToursUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// HU-COM-006: the public offer. The use cases already existed; this wires them to the controller.
@Configuration
class PublicOfferConfiguration {
    @Bean ListPublicTours listPublicTours(TenantDirectory directory, CatalogServiceRepository catalog,
            QuoteDiscountBreakdown quote, Clock clock) {
        return new ListPublicToursUseCase(directory, catalog, quote, clock);
    }

    @Bean GetPublicTour getPublicTour(TenantDirectory directory, CatalogServiceRepository catalog,
            QuoteDiscountBreakdown quote, DepartureReader departures, Clock clock) {
        return new GetPublicTourUseCase(directory, catalog, quote, departures, clock);
    }

    @Bean PublicOfferController publicOfferController(ListPublicTours tours, GetPublicTour tour) {
        return new PublicOfferController(tours, tour);
    }
}
