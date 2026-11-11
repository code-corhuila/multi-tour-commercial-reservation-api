package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ReservationController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ReservationHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.EvaluateApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservationDepartures;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TransportRateReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListReservationDeparturesUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.QuoteReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReservationPricing;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// HU-COM-007: the departures of a tour and the quote. Only this class knows which adapter backs each port.
// The ports whose adapters arrive with the persistence stack (departures, transport fare) are injected lazily: the context
// starts without them and the first call that needs one fails until its adapter is a bean.
@Configuration
class ReservationConfiguration {

    @Bean
    ReservationPricing reservationPricing(QuoteDiscountBreakdown discounts, EvaluateApplicableDiscountRules evaluation,
            @Lazy TransportRateReader transport) {
        return new ReservationPricing(discounts, evaluation, transport);
    }

    @Bean
    QuoteReservation quoteReservation(CatalogServiceRepository catalog, ReservationPricing pricing, Clock clock) {
        return new QuoteReservationUseCase(catalog, pricing, clock);
    }

    @Bean
    ListReservationDepartures listReservationDepartures(CatalogServiceRepository catalog, @Lazy DepartureReader departures,
            Clock clock) {
        return new ListReservationDeparturesUseCase(catalog, departures, clock);
    }

    @Bean
    ReservationController reservationController(ListReservationDepartures departures, QuoteReservation quote) {
        return new ReservationController(departures, quote);
    }

    @Bean
    WebMvcConfigurer reservationAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new ReservationHttpAccess()).addPathPatterns(
                        "/api/v1/commercial-reservation/reservation-quotes", "/api/v1/commercial-reservation/reservations",
                        "/api/v1/commercial-reservation/reservations/*",
                        "/api/v1/commercial-reservation/catalog/services/*/departures");
            }
        };
    }
}
