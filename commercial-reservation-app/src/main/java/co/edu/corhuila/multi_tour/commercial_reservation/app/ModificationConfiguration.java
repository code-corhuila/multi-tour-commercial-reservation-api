package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentModification;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ModificationController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ModificationHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPendingDifferenceDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservationModification;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ModifyReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteModification;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingDifferenceDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetReservationModificationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListReservationModificationsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ModifyReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.QuoteModificationUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// HU-COM-012: the quote, the modification and its history. Only this class knows which adapter backs each port: the terms, the
// pending-difference row and the history are real (JDBC); the excess goes to the decision of HU-COM-016 (FinancialDecisionOpening). The ports
// of the reservation stack are injected lazily, as in CancellationConfiguration.
@Configuration
class ModificationConfiguration {

    @Bean
    ChangePolicyReader changePolicyReader(JdbcTemplate jdbc) {
        return new JdbcChangePolicyReader(jdbc);
    }

    @Bean
    PendingDifferenceDeadlines pendingDifferenceDeadlines(JdbcTemplate jdbc) {
        return new JdbcPendingDifferenceDeadlines(jdbc);
    }

    @Bean
    ReservationModifications reservationModifications(JdbcTemplate jdbc) {
        return new JdbcReservationModifications(jdbc);
    }

    @Bean
    QuoteModification quoteModification(@Lazy ReservationRepository reservations, @Lazy DepartureReader departures, ChangePolicyReader policies,
            Clock clock) {
        return new QuoteModificationUseCase(reservations, departures, policies, clock);
    }

    @Bean
    ModifyReservation modifyReservation(@Lazy ReservationRepository reservations, @Lazy DepartureReader departures, ChangePolicyReader policies,
            @Lazy PaymentConditionReader conditions, @Lazy SalidaCapacityHolder holder, @Lazy ExcessDecisionOpener excess,
            PendingDifferenceDeadlines deadlines, ReservationModifications history, @Lazy PaymentEventOutbox outbox,
            @Lazy PaymentTransaction transaction, Clock clock) {
        return new ModifyReservationUseCase(reservations, departures, policies, conditions, holder, excess, deadlines, history, outbox, transaction, clock);
    }

    @Bean
    ListReservationModifications listReservationModifications(@Lazy ReservationRepository reservations, ReservationModifications history) {
        return new ListReservationModificationsUseCase(reservations, history);
    }

    @Bean
    GetReservationModification getReservationModification(ReservationModifications history) {
        return new GetReservationModificationUseCase(history);
    }

    @Bean
    IdempotentModification idempotentModification(ModifyReservation modification, GetReservationModification reads, IdempotencyKeys keys,
            PlatformTransactionManager manager) {
        return new IdempotentModification(modification, reads, keys, new TransactionTemplate(manager));
    }

    @Bean
    ModificationController modificationController(QuoteModification quote, ListReservationModifications history, @Lazy GetReservation reservations,
            IdempotentModification writes, Clock clock) {
        return new ModificationController(quote, history, reservations, writes, clock);
    }

    @Bean
    WebMvcConfigurer modificationAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new ModificationHttpAccess()).addPathPatterns(
                        "/api/v1/commercial-reservation/reservations/*/modification-quote", "/api/v1/commercial-reservation/reservations/*/modifications");
            }
        };
    }
}
