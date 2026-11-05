package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.OutboxRelayController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcOutboxDeliveryStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RelayOutboxDelivery;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OutboxDeliveryStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RelayOutboxDeliveryUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-015: the three outbox routes of the relay (job 7, ADR-004 D4).
@Configuration
class OutboxRelayConfiguration {

    @Bean
    OutboxDeliveryStore outboxDeliveryStore(JdbcTemplate jdbc) {
        return new JdbcOutboxDeliveryStore(jdbc);
    }

    @Bean
    RelayOutboxDelivery relayOutboxDelivery(OutboxDeliveryStore store, Clock clock) {
        return new RelayOutboxDeliveryUseCase(store, clock);
    }

    @Bean
    OutboxRelayController outboxRelayController(RelayOutboxDelivery relay) {
        return new OutboxRelayController(relay);
    }
}
