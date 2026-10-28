package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.DiscountRuleController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.DiscountRuleHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentDiscountRuleCreation;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory.InMemoryDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.InactivateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CreateDiscountRuleUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetDiscountRuleUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.InactivateDiscountRuleUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class DiscountRuleConfiguration {
    // TEMPORARY: volatile stand-in for the three ports until the JDBC adapter PR replaces this one bean.
    @Bean InMemoryDiscountRules temporaryDiscountRules() { return new InMemoryDiscountRules(); }

    @Bean CreateDiscountRule createDiscountRule(DiscountRuleRepository repository, DiscountRuleChangeOutbox outbox,
            DiscountRuleTransaction transaction, IdGenerator ids, Clock clock) {
        return new CreateDiscountRuleUseCase(repository, outbox, transaction, ids, clock);
    }

    @Bean GetDiscountRule getDiscountRule(DiscountRuleRepository repository) {
        return new GetDiscountRuleUseCase(repository);
    }

    @Bean InactivateDiscountRule inactivateDiscountRule(DiscountRuleRepository repository,
            DiscountRuleChangeOutbox outbox, DiscountRuleTransaction transaction, Clock clock) {
        return new InactivateDiscountRuleUseCase(repository, outbox, transaction, clock);
    }

    @Bean IdempotentDiscountRuleCreation idempotentDiscountRuleCreation(CreateDiscountRule create, GetDiscountRule get,
            IdempotencyKeys keys, PlatformTransactionManager manager) {
        return new IdempotentDiscountRuleCreation(create, get, keys, new TransactionTemplate(manager));
    }

    @Bean DiscountRuleController discountRuleController(IdempotentDiscountRuleCreation creation, GetDiscountRule get,
            InactivateDiscountRule inactivate) {
        return new DiscountRuleController(creation, get, inactivate);
    }

    @Bean WebMvcConfigurer discountRuleAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new DiscountRuleHttpAccess())
                        .addPathPatterns("/api/v1/commercial-reservation/discount-rules/**");
            }
        };
    }
}
