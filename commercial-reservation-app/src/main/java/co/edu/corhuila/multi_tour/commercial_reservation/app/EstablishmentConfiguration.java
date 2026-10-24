package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.*;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory.InMemoryEstablishments;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAssociatedEstablishmentKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcEstablishmentImages;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.*;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.*;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.*;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
class EstablishmentConfiguration {
    @Bean EstablishmentStores establishmentStores(ObjectProvider<JdbcTemplate> templates,
            ObjectProvider<PlatformTransactionManager> managers) {
        JdbcTemplate jdbc = templates.getIfAvailable();
        if (jdbc == null) {
            var memory = new InMemoryEstablishments();
            return new EstablishmentStores(memory, memory, memory);
        }
        var transactions = new TransactionTemplate(managers.getObject());
        var repository = new JdbcAssociatedEstablishmentRepository(jdbc, transactions);
        var keys = new JdbcAssociatedEstablishmentKeys(jdbc, new JdbcIdempotencyKeys(jdbc), repository, transactions);
        return new EstablishmentStores(repository, keys, new JdbcEstablishmentImages(jdbc, transactions));
    }
    @Bean AssociatedEstablishmentRepository establishmentRepository(EstablishmentStores stores) {
        return stores.repository();
    }
    @Bean AssociatedEstablishmentKeys establishmentKeys(EstablishmentStores stores) {
        return stores.keys();
    }
    @Bean EstablishmentImages establishmentImages(EstablishmentStores stores) {
        return stores.images();
    }
    @Bean RegisterAssociatedEstablishment registerEstablishment(AssociatedEstablishmentRepository repository,
            AssociatedEstablishmentKeys keys, IdGenerator ids, Clock clock) {
        return new RegisterAssociatedEstablishmentUseCase(repository, keys, ids, clock);
    }
    @Bean GetAssociatedEstablishment getEstablishment(AssociatedEstablishmentRepository repository) {
        return new GetAssociatedEstablishmentUseCase(repository);
    }
    @Bean ListAssociatedEstablishments listEstablishments(AssociatedEstablishmentRepository repository) {
        return new ListAssociatedEstablishmentsUseCase(repository);
    }
    @Bean InactivateAssociatedEstablishment inactivateEstablishment(AssociatedEstablishmentRepository repository, Clock clock) {
        return new InactivateAssociatedEstablishmentUseCase(repository, clock);
    }
    @Bean ReactivateAssociatedEstablishment reactivateEstablishment(AssociatedEstablishmentRepository repository, Clock clock) {
        return new ReactivateAssociatedEstablishmentUseCase(repository, clock);
    }
    @Bean EstablishmentController establishmentController(RegisterAssociatedEstablishment register,
            GetAssociatedEstablishment get, ListAssociatedEstablishments list, InactivateAssociatedEstablishment inactivate,
            ReactivateAssociatedEstablishment reactivate, AssociatedEstablishmentRepository repository,
            EstablishmentImages images) {
        return new EstablishmentController(register, get, list, inactivate, reactivate, repository, images);
    }
    @Bean WebMvcConfigurer establishmentAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new EstablishmentHttpAccess()).addPathPatterns("/api/v1/commercial-reservation/associated-establishments/**");
            }
        };
    }

    record EstablishmentStores(AssociatedEstablishmentRepository repository, AssociatedEstablishmentKeys keys,
            EstablishmentImages images) { }
}
