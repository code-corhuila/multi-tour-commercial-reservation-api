package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash.UnverifiedCashSession;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDuePaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcInitialDeadlineRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcInitialWaitPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.treatment.DeferredPaymentSettlement;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DuePaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InitialDeadlineRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InitialWaitPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentDeadlineWriter;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentExtensionPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPendingPaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineWriter;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerProfileReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentExtensionPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingPaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCancellation;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-009 and HU-COM-010: the JDBC adapters of the payment ports. Only this class knows which adapter backs each port, so a route
// family never answers 500 for a missing bean: every port the payment use cases read has a bean here or in the rest of this stack.
@Configuration
class PaymentPersistenceConfiguration {

    @Bean
    PaymentTransaction paymentTransaction(PlatformTransactionManager manager) {
        return new JdbcPaymentTransaction(new TransactionTemplate(manager));
    }

    @Bean
    PaymentEventOutbox paymentEventOutbox(JdbcTemplate jdbc, @Lazy CustomerProfileReader customers) {
        return new JdbcPaymentEventOutbox(jdbc, new JdbcClientNoticeOutbox(jdbc, customers));
    }

    @Bean
    SalidaCapacityHolder salidaCapacityHolder(JdbcTemplate jdbc) {
        return new JdbcSalidaCapacityHolder(jdbc);
    }

    @Bean
    PaymentProofStore paymentProofStore(JdbcTemplate jdbc) {
        return new JdbcPaymentProofStore(jdbc);
    }

    @Bean
    PaymentProofReader paymentProofReader(JdbcTemplate jdbc, JdbcReservationRepository reservations) {
        return new JdbcPaymentProofReader(jdbc, reservations);
    }

    @Bean
    PaymentParameterRepository paymentParameterRepository(JdbcTemplate jdbc) {
        return new JdbcPaymentParameterRepository(jdbc);
    }

    @Bean
    PaymentConditionReader paymentConditionReader(JdbcTemplate jdbc) {
        return new JdbcPaymentConditionReader(jdbc);
    }

    @Bean
    PaymentDeadlineReader paymentDeadlineReader(JdbcTemplate jdbc) {
        return new JdbcPaymentDeadlineReader(jdbc);
    }

    // HU-COM-011: the adapters of the deadline, the follow-up, the pending list and the cancellation; their routes are in PaymentDeadlineController and PaymentExpiryController.
    @Bean
    PaymentDeadlineWriter paymentDeadlineWriter(JdbcTemplate jdbc) {
        return new JdbcPaymentDeadlineWriter(jdbc);
    }

    @Bean
    PaymentExtensionPolicy paymentExtensionPolicy(JdbcTemplate jdbc) {
        return new JdbcPaymentExtensionPolicy(jdbc);
    }

    @Bean
    PaymentFollowupRepository paymentFollowupRepository(JdbcTemplate jdbc) {
        return new JdbcPaymentFollowupRepository(jdbc);
    }

    @Bean
    PendingPaymentQueries pendingPaymentQueries(JdbcTemplate jdbc) {
        return new JdbcPendingPaymentQueries(jdbc);
    }

    @Bean
    DuePaymentQueries duePaymentQueries(JdbcTemplate jdbc) {
        return new JdbcDuePaymentQueries(jdbc);
    }

    // HU-COM-011 RN-01: the creation writes the initial deadline from the initial wait of the payment condition.
    @Bean
    InitialWaitPolicy initialWaitPolicy(JdbcTemplate jdbc) {
        return new JdbcInitialWaitPolicy(jdbc);
    }

    @Bean
    InitialDeadlineRecorder initialDeadlineRecorder(JdbcTemplate jdbc) {
        return new JdbcInitialDeadlineRecorder(jdbc);
    }

    @Bean
    ReservationCancellation reservationCancellation(JdbcTemplate jdbc, @Lazy CustomerProfileReader customers) {
        return new JdbcReservationCancellation(jdbc, customers);
    }

    // The cash domain is not wired yet: this stand-in refuses every cash payment with 422 (declared debt DD-2) and never invents an income.
    @Bean
    UnverifiedCashSession unverifiedCashSession() {
        return new UnverifiedCashSession();
    }

    // HU-COM-016 and HU-COM-017 are not built (declared debts DD-5 and DD-9); the cancellation for non-payment is real above. The amounts stay on the decided attempt.
    @Bean
    CreditBalanceRecorder deferredPaymentSettlement() {
        DeferredPaymentSettlement standIn = new DeferredPaymentSettlement();
        return standIn::recordExcess; // only the credit balance stays a stand-in: the treatment of a value is the real FinancialDecisionOpening
    }
}
