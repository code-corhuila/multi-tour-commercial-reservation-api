package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentReservationCreation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentWrites;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ReservationController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ReservationHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.EvaluateApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetOwnReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListOwnReservations;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservationDepartures;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservations;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByEmail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRegisteredOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcOpenProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OpenProposalReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ResolveNextAction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ResolveReservationDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ResolveNextActionUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ResolveReservationDeadlineUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InitialDeadlineRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InitialWaitPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OwnReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TransportRateReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CreateReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetOwnReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListReservationDeparturesUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListOwnReservationsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListReservationsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.QuoteReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReservableDeparture;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReservationPricing;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// HU-COM-007: the quote, the creation and the reads of a reservation. Only this class knows which adapter backs each port.
// The ports whose adapters arrive with the persistence stack (reservations, departures, tenants, payment options, transport fare)
// are injected lazily: the context starts without them and the first call that needs one fails until its adapter is a bean.
@Configuration
class ReservationConfiguration {

    @Bean
    ReservationPricing reservationPricing(QuoteDiscountBreakdown discounts, EvaluateApplicableDiscountRules evaluation,
            @Lazy TransportRateReader transport) {
        return new ReservationPricing(discounts, evaluation, transport);
    }

    @Bean
    ReservableDeparture reservableDeparture(@Lazy DepartureReader departures, Clock clock) {
        return new ReservableDeparture(departures, clock);
    }

    @Bean
    QuoteReservation quoteReservation(CatalogServiceRepository catalog, ReservationPricing pricing, ReservableDeparture departures,
            Clock clock, @Lazy TenantDirectory tenants) {
        return new QuoteReservationUseCase(catalog, pricing, departures, clock, tenants);
    }

    @Bean
    CreateReservation createReservation(@Lazy TenantDirectory tenants, CustomerRepository customers, @Lazy CustomerByAccount accounts,
            CatalogServiceRepository catalog, ReservableDeparture departures, ReservationPricing pricing,
            @Lazy EnabledPaymentOptions options, @Lazy ReservationRepository reservations, IdGenerator ids, Clock clock,
            CustomerRegisteredOutbox customerEvents, CustomerByEmail emails, @Lazy InitialWaitPolicy waits,
            @Lazy InitialDeadlineRecorder initialDeadlines) {
        return new CreateReservationUseCase(tenants, customers, accounts, catalog, departures, pricing, options, reservations, ids, clock,
                customerEvents, emails, waits, initialDeadlines);
    }

    @Bean
    ListReservationDepartures listReservationDepartures(CatalogServiceRepository catalog, @Lazy DepartureReader departures,
            Clock clock) {
        return new ListReservationDeparturesUseCase(catalog, departures, clock);
    }

    @Bean
    ListReservations listReservations(@Lazy ReservationQueries queries) {
        return new ListReservationsUseCase(queries);
    }

    @Bean
    OpenProposalReader openProposalReader(JdbcTemplate jdbc) {
        return new JdbcOpenProposal(jdbc); // the proposal is HU-COM-018's: a PENDING one of an OPEN episode
    }

    @Bean
    ResolveNextAction resolveNextAction(@Lazy PaymentDeadlineReader deadlines, @Lazy DepartureReader departures,
            OpenProposalReader proposals, Clock clock) {
        return new ResolveNextActionUseCase(deadlines, departures, proposals, clock);
    }

    @Bean
    ResolveReservationDeadline resolveReservationDeadline(@Lazy PaymentDeadlineReader deadlines, @Lazy DepartureReader departures) {
        return new ResolveReservationDeadlineUseCase(deadlines, departures);
    }

    @Bean
    ListOwnReservations listOwnReservations(@Lazy CustomerByAccount accounts, @Lazy OwnReservationQueries queries) {
        return new ListOwnReservationsUseCase(accounts, queries);
    }

    @Bean
    GetOwnReservation getOwnReservation(@Lazy CustomerByAccount accounts, @Lazy OwnReservationQueries queries) {
        return new GetOwnReservationUseCase(accounts, queries);
    }

    @Bean
    GetReservation getReservation(@Lazy ReservationQueries queries) {
        return new GetReservationUseCase(queries);
    }

    @Bean
    IdempotentReservationCreation idempotentReservationCreation(CreateReservation create, @Lazy ReservationQueries queries,
            @Lazy CustomerByAccount accounts, CustomerRepository customers,
            IdempotentWrites writes) {
        return new IdempotentReservationCreation(create, queries, accounts, customers, writes);
    }

    @Bean
    ReservationController reservationController(ListReservationDepartures departures, QuoteReservation quote,
            IdempotentReservationCreation creation, ListReservations list, GetReservation get, ListOwnReservations listOwn,
            GetOwnReservation getOwn, ResolveNextAction nextAction, ResolveReservationDeadline deadline, Clock clock) {
        return new ReservationController(departures, quote, creation, list, get, listOwn, getOwn, nextAction, deadline, clock);
    }

    @Bean
    WebMvcConfigurer reservationAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new ReservationHttpAccess()).addPathPatterns(
                        "/api/v1/commercial-reservation/reservation-quotes", "/api/v1/commercial-reservation/own/reservation-quotes",
                        "/api/v1/commercial-reservation/reservations",
                        "/api/v1/commercial-reservation/reservations/*", "/api/v1/commercial-reservation/reservations/*/discount-breakdown",
                        "/api/v1/commercial-reservation/catalog/services/*/departures");
            }
        };
    }
}
