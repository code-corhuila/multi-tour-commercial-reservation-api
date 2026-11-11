package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentWrites;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentRefundDetermination;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentRefundWrites;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.RefundHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.RefundInternalController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.RefundReadController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.RefundWriteController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.calendar.WeekdayBusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExecuteRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageRefundDecisions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadRefunds;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RefundAutomation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.BusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DetermineRefundUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ExecuteRefundUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.FinancialDecisionOpening;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ManageRefundDecisionsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReadRefundsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RefundAutomationUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-016: the decisions and the refunds are real (JDBC over the tables and guards of the db), and one FinancialDecisionOpening answers the four ports that
// 010, 011, 012 and 013 declared and left as stand-ins. The remaining stand-in is only the credit balance (HU-COM-017), so the
// CancelledValueRecorder has a single candidate. The business calendar counts Monday to Friday until the holidays have a source (DP-03, debt DD-6). The saga and
// the cash step (HU-WFL-003, HU-CSH-001) and job 2 (HU-WRK-002) are other repositories; the routes follow in the next slices.
@Configuration
class RefundConfiguration {

    @Bean
    FinancialDecisionRepository financialDecisionRepository(JdbcTemplate jdbc) {
        return new JdbcFinancialDecisionRepository(jdbc);
    }

    @Bean
    RefundAuthorizationRepository refundAuthorizationRepository(JdbcTemplate jdbc) {
        return new JdbcRefundAuthorizationRepository(jdbc);
    }

    @Bean
    RefundAuthorizedOutbox refundAuthorizedOutbox(JdbcTemplate jdbc) {
        return new JdbcRefundAuthorizedOutbox(jdbc);
    }

    @Bean
    BusinessCalendar businessCalendar() {
        return new WeekdayBusinessCalendar();
    }

    @Bean
    FinancialDecisionOpening financialDecisionOpening(@Lazy FinancialDecisionRepository decisions, @Lazy RefundAuthorizationRepository refunds,
            BusinessCalendar calendar, @Lazy RefundAuthorizedOutbox outbox, IdGenerator ids, Clock clock) {
        return new FinancialDecisionOpening(decisions, refunds, calendar, outbox, ids, clock);
    }

    @Bean
    ManageRefundDecisions manageRefundDecisions(FinancialDecisionRepository decisions, RefundAuthorizationRepository refunds, BusinessCalendar calendar,
            RefundAuthorizedOutbox outbox, IdGenerator ids, @Lazy PaymentTransaction transaction, Clock clock) {
        return new ManageRefundDecisionsUseCase(decisions, refunds, calendar, outbox, ids, transaction, clock);
    }

    @Bean
    RefundQueries refundQueries(JdbcTemplate jdbc) {
        return new JdbcRefundQueries(jdbc);
    }

    @Bean
    ReadRefunds readRefunds(RefundQueries queries, FinancialDecisionRepository decisions, RefundAuthorizationRepository refunds, Clock clock) {
        return new ReadRefundsUseCase(queries, decisions, refunds, clock);
    }

    @Bean
    RefundReadController refundReadController(ReadRefunds read) {
        return new RefundReadController(read);
    }

    @Bean
    IdempotentRefundWrites idempotentRefundWrites(ManageRefundDecisions manage, ReadRefunds read, IdempotentWrites writes) {
        return new IdempotentRefundWrites(manage, read, writes);
    }

    @Bean
    RefundWriteController refundWriteController(IdempotentRefundWrites writes) {
        return new RefundWriteController(writes);
    }

    // The role gate of the public routes runs before the body and before any load (AC-16.3). The internal routes judge the SERVICE role themselves.
    @Bean
    WebMvcConfigurer refundAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RefundHttpAccess()).addPathPatterns("/api/v1/commercial-reservation/financial-decisions/**",
                        "/api/v1/commercial-reservation/refund-authorizations/**");
            }
        };
    }

    @Bean
    ExecuteRefund executeRefund(RefundAuthorizationRepository refunds, @Lazy PaymentTransaction transaction, Clock clock) {
        return new ExecuteRefundUseCase(refunds, transaction, clock);
    }

    @Bean
    DetermineRefund determineRefund(@Lazy ReservationReader reservations, RefundAutomation automation) {
        return new DetermineRefundUseCase(reservations, automation);
    }

    @Bean
    IdempotentRefundDetermination idempotentRefundDetermination(DetermineRefund determine, ExecuteRefund reads, IdempotentWrites writes) {
        return new IdempotentRefundDetermination(determine, reads, writes);
    }

    @Bean
    RefundInternalController refundInternalController(ExecuteRefund execute, RefundAutomation automation, IdempotentRefundDetermination determination) {
        return new RefundInternalController(execute, automation, determination);
    }

    @Bean
    RefundAutomation refundAutomation(FinancialDecisionRepository decisions, RefundAuthorizationRepository refunds, BusinessCalendar calendar,
            RefundAuthorizedOutbox outbox, IdGenerator ids, @Lazy PaymentTransaction transaction, Clock clock) {
        return new RefundAutomationUseCase(decisions, refunds, calendar, outbox, ids, transaction, clock);
    }
}
