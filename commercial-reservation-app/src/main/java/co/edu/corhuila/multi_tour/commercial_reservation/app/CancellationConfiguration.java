package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentWrites;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CancellationController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CancellationHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cancellation.NoCancellationPenaltyPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCancellationRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReprogrammingMark;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CancelReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReprogrammingMarkReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CancelReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetCancellationRecordUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.QuoteCancellationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// HU-COM-013: the quote, the cancellation at the customer's request and its record. Only this class knows which adapter backs each port:
// the record, the rescheduling mark (the open episode of HU-COM-018) and the financial decision (HU-COM-016) are real; the penalty policy is still
// a documented stand-in (declared debt DD-3). The ports of the reservation stack are injected lazily, as in PaymentConfiguration.
@Configuration
class CancellationConfiguration {

    @Bean
    CancellationRecords cancellationRecords(JdbcTemplate jdbc) {
        return new JdbcCancellationRecords(jdbc);
    }

    @Bean
    CancellationPolicyReader cancellationPolicyReader() {
        return new NoCancellationPenaltyPolicy();
    }

    @Bean
    ReprogrammingMarkReader reprogrammingMarkReader(JdbcTemplate jdbc) {
        return new JdbcReprogrammingMark(jdbc);
    }

    @Bean
    QuoteCancellation quoteCancellation(@Lazy ReservationRepository reservations, @Lazy DepartureReader departures,
            CancellationPolicyReader policy, ReprogrammingMarkReader marks, Clock clock) {
        return new QuoteCancellationUseCase(reservations, departures, policy, marks, clock);
    }

    @Bean
    CancelReservation cancelReservation(@Lazy ReservationRepository reservations, @Lazy DepartureReader departures,
            @Lazy SalidaCapacityHolder holder, CancellationPolicyReader policy, ReprogrammingMarkReader marks,
            FinancialDecisionOpener decisions, @Lazy CancellationRecords records, @Lazy PaymentEventOutbox outbox,
            @Lazy PaymentTransaction transaction, Clock clock, @Lazy TenantStatusReader statuses) {
        return new CancelReservationUseCase(reservations, departures, holder, policy, marks, decisions, records, outbox, transaction, clock, statuses);
    }

    @Bean
    GetCancellationRecord getCancellationRecord(@Lazy CancellationRecords records) {
        return new GetCancellationRecordUseCase(records);
    }

    @Bean
    IdempotentCancellation idempotentCancellation(CancelReservation cancellation, GetCancellationRecord reads, IdempotentWrites writes) {
        return new IdempotentCancellation(cancellation, reads, writes);
    }

    @Bean
    CancellationController cancellationController(QuoteCancellation quote, GetCancellationRecord record, IdempotentCancellation writes) {
        return new CancellationController(quote, record, writes);
    }

    @Bean
    WebMvcConfigurer cancellationAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new CancellationHttpAccess()).addPathPatterns(
                        "/api/v1/commercial-reservation/reservations/*/cancellation", "/api/v1/commercial-reservation/reservations/*/cancellation-quote");
            }
        };
    }
}
