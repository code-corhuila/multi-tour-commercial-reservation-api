package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.HealthController;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The only place that knows every concrete type; adapters never instantiate each other.
@Configuration
class CommercialReservationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    HealthController healthController(Clock clock) {
        return new HealthController(clock);
    }
}
