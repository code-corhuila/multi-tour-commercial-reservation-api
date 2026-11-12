package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.RetentionInternalController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRetentionDeletions;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRetentionEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRetentionFactsReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.retention.DeferredReservationClosing;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationClosingReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionDeletions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionFactsReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DeleteSensitiveData;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDueSensitiveData;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DeleteSensitiveDataUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListDueSensitiveDataUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-019: the readers that decide the retention anchor. The facts are real (the local mirror, the reservation and its decisions); the closing of a cancelled
// reservation is a documented stand-in (declared debt DD-2) until HU-COM-016 and HU-COM-017 record it.
@Configuration
class RetentionConfiguration {

    @Bean
    RetentionFactsReader retentionFactsReader(JdbcTemplate jdbc) {
        return new JdbcRetentionFactsReader(jdbc);
    }

    @Bean
    ReservationClosingReader reservationClosingReader() {
        return new DeferredReservationClosing();
    }

    @Bean
    RetentionDeletions retentionDeletions(JdbcTemplate jdbc) {
        return new JdbcRetentionDeletions(jdbc);
    }

    @Bean
    RetentionEventOutbox retentionEventOutbox(JdbcTemplate jdbc) {
        return new JdbcRetentionEventOutbox(jdbc);
    }

    @Bean
    DeleteSensitiveData deleteSensitiveData(RetentionFactsReader facts, ReservationClosingReader closings, RetentionDeletions deletions, RetentionEventOutbox outbox,
            @Lazy PaymentTransaction transaction, Clock clock) {
        return new DeleteSensitiveDataUseCase(facts, closings, deletions, outbox, transaction, clock);
    }

    @Bean
    ListDueSensitiveData listDueSensitiveData(RetentionFactsReader facts, ReservationClosingReader closings, RetentionDeletions deletions, Clock clock) {
        return new ListDueSensitiveDataUseCase(facts, closings, deletions, clock);
    }

    @Bean
    RetentionInternalController retentionInternalController(ListDueSensitiveData list, DeleteSensitiveData deletion) {
        return new RetentionInternalController(list, deletion);
    }
}
