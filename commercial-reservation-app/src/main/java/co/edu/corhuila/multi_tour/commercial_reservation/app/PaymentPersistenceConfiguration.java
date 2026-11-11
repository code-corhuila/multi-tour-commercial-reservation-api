package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
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
}
