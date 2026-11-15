package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.InternalDepartureController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcInternalDepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListInternalDepartureReservations;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InternalDepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListInternalDepartureReservationsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

// G-API6-1: the reservations of a departure for a service (operations-cost) over the JDBC adapter.
@Configuration
class InternalDepartureReservationConfiguration {

    @Bean
    InternalDepartureReservationQueries internalDepartureReservationQueries(JdbcTemplate jdbc) {
        return new JdbcInternalDepartureReservationQueries(jdbc);
    }

    @Bean
    ListInternalDepartureReservations listInternalDepartureReservations(InternalDepartureReservationQueries queries) {
        return new ListInternalDepartureReservationsUseCase(queries);
    }

    @Bean
    InternalDepartureController internalDepartureController(ListInternalDepartureReservations reservations) {
        return new InternalDepartureController(reservations);
    }
}
