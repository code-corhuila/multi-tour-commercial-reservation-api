package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.*;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory.InMemoryEstablishments;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.*;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.*;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.*;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
class EstablishmentConfiguration {
    @Bean InMemoryEstablishments establishments() { return new InMemoryEstablishments(); }
    @Bean RegisterAssociatedEstablishment registerEstablishment(InMemoryEstablishments store, IdGenerator ids, Clock clock) {
        return new RegisterAssociatedEstablishmentUseCase(store, store, ids, clock);
    }
    @Bean GetAssociatedEstablishment getEstablishment(InMemoryEstablishments store) {
        return new GetAssociatedEstablishmentUseCase(store);
    }
    @Bean ListAssociatedEstablishments listEstablishments(InMemoryEstablishments store) {
        return new ListAssociatedEstablishmentsUseCase(store);
    }
    @Bean InactivateAssociatedEstablishment inactivateEstablishment(InMemoryEstablishments store, Clock clock) {
        return new InactivateAssociatedEstablishmentUseCase(store, clock);
    }
    @Bean ReactivateAssociatedEstablishment reactivateEstablishment(InMemoryEstablishments store, Clock clock) {
        return new ReactivateAssociatedEstablishmentUseCase(store, clock);
    }
    @Bean EstablishmentController establishmentController(RegisterAssociatedEstablishment register,
            GetAssociatedEstablishment get, ListAssociatedEstablishments list, InactivateAssociatedEstablishment inactivate,
            ReactivateAssociatedEstablishment reactivate, InMemoryEstablishments store) {
        return new EstablishmentController(register, get, list, inactivate, reactivate, store, store);
    }
    @Bean WebMvcConfigurer establishmentAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new EstablishmentHttpAccess()).addPathPatterns("/api/v1/associated-establishments/**");
            }
        };
    }
}
