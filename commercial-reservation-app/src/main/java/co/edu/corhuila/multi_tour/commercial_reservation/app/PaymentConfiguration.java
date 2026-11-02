package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentPaymentRegistration;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentProofController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentProofHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentSettingsController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetOwnReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPaymentQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPaymentProofs;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManagePaymentParameters;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadPaymentProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadPaymentProofs;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCashPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterTransfer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashIncomeRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashSessionStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetPaymentQuoteUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ManagePaymentParametersUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListPaymentProofsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentProofQueriesUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReadPaymentProofFileUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterCashPaymentUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterTransferUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// HU-COM-009: the registration of a transfer and of cash and the history of the payments. Only this class knows which adapter backs
// each port. The ports whose adapters arrive with the persistence slices are injected lazily, as in ReservationConfiguration.
@Configuration
class PaymentConfiguration {

    @Bean
    RegisterTransfer registerTransfer(@Lazy ReservationRepository reservations, @Lazy DepartureReader departures,
            @Lazy PaymentDeadlineReader deadlines, @Lazy EnabledPaymentOptions options, @Lazy PaymentConditionReader conditions,
            @Lazy SalidaCapacityHolder holder, @Lazy PaymentProofStore proofs, @Lazy PaymentEventOutbox outbox,
            @Lazy PaymentTransaction transaction, IdGenerator ids, Clock clock) {
        return new RegisterTransferUseCase(reservations, departures, deadlines, options, conditions, holder, proofs, outbox, transaction,
                ids, clock);
    }

    @Bean
    RegisterCashPayment registerCashPayment(@Lazy ReservationRepository reservations, @Lazy DepartureReader departures,
            @Lazy PaymentDeadlineReader deadlines, @Lazy EnabledPaymentOptions options, @Lazy PaymentConditionReader conditions,
            @Lazy SalidaCapacityHolder holder, @Lazy PaymentEventOutbox outbox, @Lazy PaymentTransaction transaction,
            @Lazy CashSessionStatus session, @Lazy CashIncomeRecorder income, @Lazy CreditBalanceRecorder credit, IdGenerator ids,
            Clock clock) {
        return new RegisterCashPaymentUseCase(reservations, departures, deadlines, options, conditions, holder, outbox, transaction,
                session, income, credit, ids, clock);
    }

    @Bean
    IdempotentPaymentRegistration idempotentPaymentRegistration(RegisterTransfer transfers, RegisterCashPayment cash,
            @Lazy ReservationRepository reservations, IdempotencyKeys keys, PlatformTransactionManager manager) {
        return new IdempotentPaymentRegistration(transfers, cash, reservations, keys, new TransactionTemplate(manager));
    }

    @Bean
    PaymentController paymentController(IdempotentPaymentRegistration registration, GetReservation get, GetOwnReservation getOwn) {
        return new PaymentController(registration, get, getOwn);
    }

    @Bean
    GetPaymentQuote getPaymentQuote(@Lazy ReservationRepository reservations, @Lazy EnabledPaymentOptions options,
            @Lazy PaymentConditionReader conditions, @Lazy PaymentDeadlineReader deadlines) {
        return new GetPaymentQuoteUseCase(reservations, options, conditions, deadlines);
    }

    @Bean
    ManagePaymentParameters managePaymentParameters(@Lazy PaymentParameterRepository parameters, @Lazy PaymentTransaction transaction,
            Clock clock) {
        return new ManagePaymentParametersUseCase(parameters, transaction, clock);
    }

    @Bean
    PaymentSettingsController paymentSettingsController(GetPaymentQuote quote, GetOwnReservation getOwn, ManagePaymentParameters parameters) {
        return new PaymentSettingsController(quote, getOwn, parameters);
    }

    @Bean
    ListPaymentProofs listPaymentProofs(@Lazy PaymentProofReader reader, Clock clock) {
        return new ListPaymentProofsUseCase(reader, clock);
    }

    @Bean
    ReadPaymentProofs readPaymentProofs(@Lazy ReservationRepository reservations, Clock clock) {
        return new PaymentProofQueriesUseCase(reservations, clock);
    }

    @Bean
    ReadPaymentProofFile readPaymentProofFile(@Lazy ReservationRepository reservations, @Lazy PaymentProofStore proofs) {
        return new ReadPaymentProofFileUseCase(reservations, proofs);
    }

    @Bean
    PaymentProofController paymentProofController(ListPaymentProofs list, ReadPaymentProofs read, ReadPaymentProofFile file,
            CustomerRepository customers) {
        return new PaymentProofController(list, read, file, customers);
    }

    @Bean
    WebMvcConfigurer paymentAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new PaymentProofHttpAccess()).addPathPatterns("/api/v1/commercial-reservation/payment-proofs",
                        "/api/v1/commercial-reservation/payment-proofs/*", "/api/v1/commercial-reservation/payment-proofs/*/file");
                registry.addInterceptor(new PaymentHttpAccess()).addPathPatterns(
                        "/api/v1/commercial-reservation/reservations/*/payments", "/api/v1/commercial-reservation/reservations/*/payments/*",
                        "/api/v1/commercial-reservation/reservations/*/payment-quote", "/api/v1/commercial-reservation/payment-parameters");
            }
        };
    }
}
