package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.HealthController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ApiErrorWriter;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.AuthFilter;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CorrelationFilter;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ErrorHandler;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.JwtPublicKeyLoader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.Rs256Verifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

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

    @Bean
    RSAPublicKey jwtPublicKey(Environment environment) {
        return new JwtPublicKeyLoader().load(environment.getProperty("JWT_PUBLIC_KEY"),
                environment.getProperty("JWT_PUBLIC_KEY_FILE"));
    }

    @Bean
    Rs256Verifier rs256Verifier(RSAPublicKey key, Clock clock, ObjectMapper json) {
        return new Rs256Verifier(key, clock, json);
    }

    @Bean
    ApiErrorWriter apiErrorWriter(ObjectMapper json) {
        return new ApiErrorWriter(json);
    }

    @Bean
    ErrorHandler errorHandler(ApiErrorWriter errors) {
        return new ErrorHandler(errors);
    }

    @Bean
    FilterRegistrationBean<CorrelationFilter> correlationFilter() {
        FilterRegistrationBean<CorrelationFilter> registration = new FilterRegistrationBean<>(new CorrelationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AuthFilter> authFilter(Rs256Verifier verifier, ApiErrorWriter errors) {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>(new AuthFilter(verifier, errors));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
