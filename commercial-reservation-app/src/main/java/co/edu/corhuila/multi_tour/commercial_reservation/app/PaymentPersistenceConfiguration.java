package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
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
    PaymentEventOutbox paymentEventOutbox(JdbcTemplate jdbc) {
        return new JdbcPaymentEventOutbox(jdbc);
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
}
