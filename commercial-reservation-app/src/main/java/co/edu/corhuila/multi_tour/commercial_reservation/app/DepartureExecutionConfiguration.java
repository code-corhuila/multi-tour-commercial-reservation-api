package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.DepartureExecutionController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureExecutionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ChangeDepartureExecution;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureExecutionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ChangeDepartureExecutionUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

// G-API4-1: the start and the end of a departure move its reservations (RN-EJE-001), over the JDBC adapters.
@Configuration
class DepartureExecutionConfiguration {

    @Bean
    DepartureExecutionReader departureExecutionReader(JdbcTemplate jdbc) {
        return new JdbcDepartureExecutionReader(jdbc);
    }

    @Bean
    ChangeDepartureExecution changeDepartureExecution(DepartureExecutionReader departures, ReservationRepository reservations, IdempotencyKeys keys,
            @Lazy PaymentTransaction transaction) {
        return new ChangeDepartureExecutionUseCase(departures, reservations, new JdbcIdempotencyStore(keys), transaction);
    }

    @Bean
    DepartureExecutionController departureExecutionController(ChangeDepartureExecution execution) {
        return new DepartureExecutionController(execution);
    }
}
