package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.DepartureReservationController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDepartureReservations;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListDepartureReservationsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-012 amendment 2026-11-10: the read of the reservations of a departure over the JDBC adapter.
@Configuration
class DepartureReservationConfiguration {

    @Bean
    DepartureReservationQueries departureReservationQueries(JdbcTemplate jdbc) {
        return new JdbcDepartureReservationQueries(jdbc);
    }

    @Bean
    ListDepartureReservations listDepartureReservations(DepartureReservationQueries queries) {
        return new ListDepartureReservationsUseCase(queries);
    }

    @Bean
    DepartureReservationController departureReservationController(ListDepartureReservations reservations) {
        return new DepartureReservationController(reservations);
    }
}
