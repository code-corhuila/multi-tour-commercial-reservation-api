package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaMirrorTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.MirrorSalidaState;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaMirrorTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.MirrorSalidaStateUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-006: the departure ports and the mirror use case. The consumer route that calls MirrorSalidaState is not built here.
@Configuration
class DepartureMirrorConfiguration {

    @Bean
    DepartureReader departureReader(JdbcTemplate jdbc) {
        return new JdbcDepartureReader(jdbc);
    }

    @Bean
    DepartureStateRepository departureStateRepository(JdbcTemplate jdbc) {
        return new JdbcDepartureStateRepository(jdbc);
    }

    @Bean
    ProcessedEvents processedEvents(JdbcTemplate jdbc) {
        return new JdbcProcessedEvents(jdbc);
    }

    @Bean
    SalidaMirrorTransaction salidaMirrorTransaction(PlatformTransactionManager manager) {
        return new JdbcSalidaMirrorTransaction(new TransactionTemplate(manager));
    }

    @Bean
    MirrorSalidaState mirrorSalidaState(DepartureStateRepository departures, ProcessedEvents processed,
            SalidaMirrorTransaction transaction, CatalogServiceRepository catalog) {
        return new MirrorSalidaStateUseCase(departures, processed, transaction, catalog);
    }
}
