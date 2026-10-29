package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.AuthorizableReasonController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.DiscountRuleHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.TransactionalAuthorizableReasons;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.AuthorizableReasonTimes;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageAuthorizableReasons;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ManageAuthorizableReasonsUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-004: the JDBC adapters of the additional discount. RegisterAdditionalDiscount is not wired here yet:
// it also needs the reservation reader (HU-COM-007), the excess treatment and the shared transaction port.
@Configuration
class AdditionalDiscountConfiguration {
    @Bean AdditionalDiscountRepository additionalDiscountRepository(JdbcTemplate jdbc) {
        return new JdbcAdditionalDiscountRepository(jdbc);
    }

    @Bean JdbcAuthorizableReasonRepository authorizableReasonRepository(JdbcTemplate jdbc, Clock clock) {
        return new JdbcAuthorizableReasonRepository(jdbc, clock);
    }

    @Bean ReservationModifiedOutbox reservationModifiedOutbox(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcReservationModifiedOutbox(jdbc, json);
    }

    @Bean ManageAuthorizableReasons manageAuthorizableReasons(AuthorizableReasonRepository reasons, IdGenerator ids) {
        return new ManageAuthorizableReasonsUseCase(reasons, ids);
    }

    @Bean TransactionalAuthorizableReasons transactionalAuthorizableReasons(ManageAuthorizableReasons reasons,
            AuthorizableReasonTimes times, IdempotencyKeys keys, PlatformTransactionManager manager) {
        return new TransactionalAuthorizableReasons(reasons, times, keys, new TransactionTemplate(manager));
    }

    @Bean AuthorizableReasonController authorizableReasonController(TransactionalAuthorizableReasons reasons) {
        return new AuthorizableReasonController(reasons);
    }

    @Bean WebMvcConfigurer authorizableReasonAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new DiscountRuleHttpAccess())
                        .addPathPatterns("/api/v1/commercial-reservation/additional-discount-reasons/**",
                                "/api/v1/commercial-reservation/additional-discount-reasons");
            }
        };
    }
}
