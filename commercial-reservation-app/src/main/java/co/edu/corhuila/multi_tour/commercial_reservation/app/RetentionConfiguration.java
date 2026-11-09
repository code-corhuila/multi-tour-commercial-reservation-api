package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRetentionFactsReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.retention.DeferredReservationClosing;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationClosingReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionFactsReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
