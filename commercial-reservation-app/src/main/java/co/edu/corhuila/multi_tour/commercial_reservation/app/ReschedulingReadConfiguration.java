package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ReschedulingReadController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingReads;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadReschedulings;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingReads;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReadReschedulingsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-018: the public reads of the rescheduling episodes (the list and the detail) over the JDBC adapters merged in the adapters pull request. The role check and the ownership of a Final
// Customer are in the use case, so no interceptor is needed for a route without a body. The reads load an episode with the JDBC repository, built here and not exposed as a bean, so this
// class does not compete with the one that wires the writes and the internal routes of the saga.
@Configuration
class ReschedulingReadConfiguration {

    @Bean
    ReschedulingReads reschedulingReads(JdbcTemplate jdbc) {
        return new JdbcReschedulingReads(jdbc, new JdbcReschedulingRepository(jdbc));
    }

    @Bean
    ReadReschedulings readReschedulings(ReschedulingReads reads, @Lazy CustomerByAccount accounts) {
        return new ReadReschedulingsUseCase(reads, accounts);
    }

    @Bean
    ReschedulingReadController reschedulingReadController(ReadReschedulings read) {
        return new ReschedulingReadController(read);
    }
}
