package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.AdditionalDiscountController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.AuthorizableReasonController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.DiscountRuleHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.TransactionalAuthorizableReasons;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.treatment.DeferredExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.AuthorizableReasonTimes;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageAuthorizableReasons;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ManageAuthorizableReasonsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterAdditionalDiscountUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-004: the JDBC adapters of the additional discount and its registration use case. The reservation reader is the
// one of HU-COM-007, the transaction is the one shared with the discount rules and the excess treatment is deferred (D-06).
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

    @Bean ExcessTreatment excessTreatment() {
        return new DeferredExcessTreatment();
    }

    @Bean RegisterAdditionalDiscount registerAdditionalDiscount(ReservationReader reservations,
            AdditionalDiscountRepository discounts, AuthorizableReasonRepository reasons, ReservationModifiedOutbox outbox,
            ExcessTreatment excess, DiscountRuleTransaction transaction, Clock clock) {
        return new RegisterAdditionalDiscountUseCase(reservations, discounts, reasons, outbox, excess, transaction, clock);
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

    @Bean AdditionalDiscountController additionalDiscountController(RegisterAdditionalDiscount discounts,
            GetReservation reservations) {
        return new AdditionalDiscountController(discounts, reservations);
    }

    // The role is checked before the query is read; the paths of the reservation are not the ones of ReservationHttpAccess.
    @Bean WebMvcConfigurer additionalDiscountAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new DiscountRuleHttpAccess()).addPathPatterns(
                        "/api/v1/commercial-reservation/reservations/*/additional-discount",
                        "/api/v1/commercial-reservation/reservations/*/additional-discount/**");
            }
        };
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
