package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CreditHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CreditWriteController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentCreditWrites;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditApplicationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceApplications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageCreditBalances;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ApplyCreditBalanceUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ManageCreditBalancesUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RecordCreditBalanceUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// HU-COM-017: the credit balance use cases and their write routes over the JDBC adapters. The role gate of the credit routes runs before the body and before any
// load (AC-17.14); the credit decision sits under financial-decisions, which RefundHttpAccess already limits to the Administrator.
@Configuration
class CreditBalanceConfiguration {

    @Bean
    RecordCreditBalanceUseCase recordCreditBalance(@Lazy FinancialDecisionRepository decisions, @Lazy CreditBalanceRepository credits, IdGenerator ids,
            @Lazy PaymentTransaction transaction, Clock clock) {
        return new RecordCreditBalanceUseCase(decisions, credits, ids, transaction, clock);
    }

    @Bean
    ApplyCreditBalanceUseCase applyCreditBalance(@Lazy CreditBalanceRepository credits, @Lazy CreditBalanceApplications applications,
            @Lazy ReservationRepository reservations, @Lazy DepartureReader departures, @Lazy PaymentDeadlineReader deadlines,
            @Lazy PaymentConditionReader conditions, @Lazy SalidaCapacityHolder holder, @Lazy PaymentEventOutbox outbox, @Lazy PaymentTransaction transaction,
            IdGenerator ids, Clock clock) {
        return new ApplyCreditBalanceUseCase(credits, applications, reservations, departures, deadlines, conditions, holder, outbox, transaction, ids, clock);
    }

    @Bean
    ManageCreditBalances manageCreditBalances(RecordCreditBalanceUseCase record, ApplyCreditBalanceUseCase apply, @Lazy CreditBalanceRepository credits,
            @Lazy CreditApplicationReader applications, @Lazy CustomerByAccount accounts, @Lazy ReservationRepository reservations) {
        return new ManageCreditBalancesUseCase(record, apply, credits, applications, accounts, reservations);
    }

    @Bean
    IdempotentCreditWrites idempotentCreditWrites(ManageCreditBalances credits, IdempotencyKeys keys, PlatformTransactionManager manager) {
        IdempotentCreditWrites.Keys store = new IdempotentCreditWrites.Keys() {
            @Override public boolean tryRegister(java.util.UUID tenantId, String key, String resourceType, java.util.UUID resourceId) {
                return keys.tryRegister(tenantId, key, resourceType, resourceId);
            }

            @Override public java.util.Optional<java.util.UUID> findResourceId(java.util.UUID tenantId, String key) {
                return keys.findResourceId(tenantId, key);
            }
        };
        return new IdempotentCreditWrites(credits, store, new TransactionTemplate(manager));
    }

    @Bean
    CreditWriteController creditWriteController(IdempotentCreditWrites writes) {
        return new CreditWriteController(writes);
    }

    @Bean
    WebMvcConfigurer creditAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new CreditHttpAccess()).addPathPatterns("/api/v1/commercial-reservation/credit-balances/**",
                        "/api/v1/commercial-reservation/credit-balance-summary");
            }
        };
    }
}
