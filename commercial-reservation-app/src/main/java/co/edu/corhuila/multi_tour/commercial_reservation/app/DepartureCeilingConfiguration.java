package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.DepartureCeilingController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureCeilings;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.SetDepartureCeiling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureCeilings;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.SetDepartureCeilingUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-012 amendment 2026-11-10: the ceiling route of the saga change-departure-vehicles over the JDBC adapters.
@Configuration
class DepartureCeilingConfiguration {

    @Bean
    DepartureCeilings departureCeilings(JdbcTemplate jdbc) {
        return new JdbcDepartureCeilings(jdbc);
    }

    @Bean
    SetDepartureCeiling setDepartureCeiling(DepartureCeilings ceilings, IdempotencyKeys keys, @Lazy PaymentTransaction transaction) {
        return new SetDepartureCeilingUseCase(ceilings, new JdbcIdempotencyStore(keys), transaction);
    }

    @Bean
    DepartureCeilingController departureCeilingController(SetDepartureCeiling ceilings) {
        return new DepartureCeilingController(ceilings);
    }
}
