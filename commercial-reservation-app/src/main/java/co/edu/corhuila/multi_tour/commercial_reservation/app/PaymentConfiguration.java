package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentDeadlineWrites;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentDeadlineController;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ExtendPaymentDeadlineUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetPaymentDeadlineUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListPaymentFollowupsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListPendingPaymentsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RecordPaymentFollowupUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExtendPaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPaymentFollowups;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPendingPayments;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RecordPaymentFollowup;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineWriter;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentExtensionPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingPaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentPaymentParameterChange;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentPaymentRegistration;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentProofDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentProofDecisionController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentProofController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentProofHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.PaymentSettingsController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DecidePaymentProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetOwnReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPaymentQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPaymentProofs;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManagePaymentParameters;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadPaymentProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RecordValidatedPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadPaymentProofs;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCashPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterTransfer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
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
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DecidePaymentProofUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetPaymentQuoteUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ManagePaymentParametersUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListPaymentProofsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentProofQueriesUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReadPaymentProofFileUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RecordValidatedPaymentUseCase;
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
    IdempotentPaymentParameterChange idempotentPaymentParameterChange(ManagePaymentParameters parameters, IdempotencyKeys keys,
            PlatformTransactionManager manager) {
        return new IdempotentPaymentParameterChange(parameters, keys, new TransactionTemplate(manager));
    }

    @Bean
    PaymentSettingsController paymentSettingsController(GetPaymentQuote quote, GetOwnReservation getOwn, ManagePaymentParameters parameters,
            IdempotentPaymentParameterChange changes) {
        return new PaymentSettingsController(quote, getOwn, parameters, changes);
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
    ReadPaymentProofFile readPaymentProofFile(@Lazy ReservationRepository reservations, @Lazy PaymentProofStore proofs,
            @Lazy CustomerByAccount accounts) {
        return new ReadPaymentProofFileUseCase(reservations, proofs, accounts);
    }

    @Bean
    PaymentProofController paymentProofController(ListPaymentProofs list, ReadPaymentProofs read, ReadPaymentProofFile file,
            CustomerRepository customers) {
        return new PaymentProofController(list, read, file, customers);
    }

    @Bean
    DecidePaymentProofUseCase decidePaymentProof(@Lazy ReservationRepository reservations, @Lazy PaymentDeadlineReader deadlines, @Lazy DepartureReader departures,
            @Lazy PaymentConditionReader conditions, @Lazy SalidaCapacityHolder holder, @Lazy ReservationCancellation cancellation,
            @Lazy CancelledValueRecorder cancelledValues, @Lazy CreditBalanceRecorder credit, @Lazy PaymentEventOutbox outbox,
            @Lazy PaymentTransaction transaction, Clock clock) {
        return new DecidePaymentProofUseCase(reservations, deadlines, departures, conditions, holder, cancellation, cancelledValues, credit, outbox,
                transaction, clock);
    }

    @Bean
    RecordValidatedPayment recordValidatedPayment(DecidePaymentProofUseCase decisions, @Lazy ReservationRepository reservations) {
        return new RecordValidatedPaymentUseCase(decisions, reservations);
    }

    @Bean
    IdempotentProofDecision idempotentProofDecision(DecidePaymentProof decisions, @Lazy ReservationRepository reservations,
            IdempotencyKeys keys, PlatformTransactionManager manager) {
        return new IdempotentProofDecision(decisions, reservations, keys, new TransactionTemplate(manager));
    }

    @Bean
    PaymentProofDecisionController paymentProofDecisionController(IdempotentProofDecision decisions, RecordValidatedPayment validated,
            CustomerRepository customers, Clock clock) {
        return new PaymentProofDecisionController(decisions, validated, customers, clock);
    }

    // HU-COM-011: the public routes of the deadline, the follow-up and the single extension (the internal routes of the job come next).
    @Bean
    RecordPaymentFollowup recordPaymentFollowup(@Lazy ReservationRepository reservations, @Lazy PaymentFollowupRepository followups,
            IdGenerator ids, @Lazy PaymentTransaction transaction, Clock clock) {
        return new RecordPaymentFollowupUseCase(reservations, followups, ids, transaction, clock);
    }

    @Bean
    ListPaymentFollowups listPaymentFollowups(@Lazy ReservationRepository reservations, @Lazy PaymentFollowupRepository followups) {
        return new ListPaymentFollowupsUseCase(reservations, followups);
    }

    @Bean
    GetPaymentDeadline getPaymentDeadline(@Lazy ReservationRepository reservations, @Lazy PaymentDeadlineReader deadlines,
            @Lazy DepartureReader departures) {
        return new GetPaymentDeadlineUseCase(reservations, deadlines, departures);
    }

    @Bean
    ListPendingPayments listPendingPayments(@Lazy PendingPaymentQueries queries) {
        return new ListPendingPaymentsUseCase(queries);
    }

    @Bean
    ExtendPaymentDeadline extendPaymentDeadline(@Lazy ReservationRepository reservations, @Lazy PaymentDeadlineReader deadlines,
            @Lazy DepartureReader departures, @Lazy PaymentExtensionPolicy policy, @Lazy PaymentDeadlineWriter writer,
            @Lazy PaymentTransaction transaction, Clock clock) {
        return new ExtendPaymentDeadlineUseCase(reservations, deadlines, departures, policy, writer, transaction, clock);
    }

    @Bean
    IdempotentDeadlineWrites idempotentDeadlineWrites(RecordPaymentFollowup followups, ExtendPaymentDeadline extensions,
            GetPaymentDeadline reads, @Lazy PaymentFollowupRepository history, IdempotencyKeys keys, PlatformTransactionManager manager) {
        return new IdempotentDeadlineWrites(followups, extensions, reads, history, keys, new TransactionTemplate(manager));
    }

    @Bean
    PaymentDeadlineController paymentDeadlineController(ListPendingPayments pending, GetPaymentDeadline deadline, ListPaymentFollowups history,
            IdempotentDeadlineWrites writes) {
        return new PaymentDeadlineController(pending, deadline, history, writes);
    }

    @Bean
    WebMvcConfigurer paymentAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new PaymentProofHttpAccess()).addPathPatterns("/api/v1/commercial-reservation/payment-proofs",
                        "/api/v1/commercial-reservation/payment-proofs/*", "/api/v1/commercial-reservation/payment-proofs/*/file",
                        "/api/v1/commercial-reservation/payment-proofs/*/approve", "/api/v1/commercial-reservation/payment-proofs/*/reject");
                registry.addInterceptor(new PaymentHttpAccess()).addPathPatterns(
                        "/api/v1/commercial-reservation/reservations/*/payments", "/api/v1/commercial-reservation/reservations/*/payments/*",
                        "/api/v1/commercial-reservation/reservations/*/payment-quote", "/api/v1/commercial-reservation/payment-parameters");
            }
        };
    }
}
