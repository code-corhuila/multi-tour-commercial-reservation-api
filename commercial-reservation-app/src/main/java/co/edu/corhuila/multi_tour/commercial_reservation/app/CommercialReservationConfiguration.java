package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.HealthController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ApiErrorWriter;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.AuthFilter;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CatalogController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CatalogAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.TransactionalCatalogUpdate;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CorrelationFilter;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ErrorHandler;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentCatalogRegistration;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.JwtPublicKeyLoader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaCapacityReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.InactivateCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReactivateCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListCatalogServicesForManagement;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateCommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateDescriptiveInformation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.policy.NoCollaboratorEditing;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CollaboratorEditingPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetCatalogServiceUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.InactivateCatalogServiceUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListCatalogServicesForManagementUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListOfferedCatalogServicesUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReactivateCatalogServiceUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterCatalogServiceUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.UpdateCommercialTermsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.UpdateDescriptiveInformationUseCase;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// The only place that knows every concrete type; adapters never instantiate each other.
@Configuration
class CommercialReservationConfiguration {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictJson() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT, MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    @Bean
    IdempotentCatalogRegistration catalogRegistration(RegisterCatalogService register, GetCatalogService get,
            IdempotencyKeys keys, PlatformTransactionManager manager) {
        return new IdempotentCatalogRegistration(register, get, keys, new TransactionTemplate(manager));
    }

    @Bean
    CatalogController catalogController(GetCatalogService get, IdempotentCatalogRegistration registration,
            ListCatalogServicesForManagement management, TransactionalCatalogUpdate updates,
            InactivateCatalogService inactivate, ReactivateCatalogService reactivate) {
        return new CatalogController(get, registration, management, updates, inactivate, reactivate);
    }

    @Bean
    CollaboratorEditingPolicy collaboratorEditingPolicy() {
        return new NoCollaboratorEditing();
    }

    @Bean
    WebMvcConfigurer catalogAuthorization(CollaboratorEditingPolicy collaboratorEditing) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new CatalogAuthorization(collaboratorEditing)).addPathPatterns("/api/v1/commercial-reservation/catalog/**");
            }
        };
    }

    @Bean
    TransactionalCatalogUpdate catalogUpdate(UpdateDescriptiveInformation description, UpdateCommercialTerms terms,
            PlatformTransactionManager manager) {
        return new TransactionalCatalogUpdate(description, terms, new TransactionTemplate(manager));
    }

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1000));
    }

    @Bean
    CatalogServiceRepository catalogServiceRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new JdbcCatalogServiceRepository(jdbc, new TransactionTemplate(manager));
    }

    @Bean
    IdGenerator idGenerator() {
        return new UuidGenerator();
    }

    @Bean
    SalidaCapacityReader salidaCapacityReader(JdbcTemplate jdbc) {
        return new JdbcSalidaCapacityReader(jdbc);
    }

    @Bean
    JdbcIdempotencyKeys idempotencyKeys(JdbcTemplate jdbc) {
        return new JdbcIdempotencyKeys(jdbc);
    }

    @Bean
    RegisterCatalogServiceUseCase registerCatalogService(CatalogServiceRepository repository, IdGenerator ids, Clock clock) {
        return new RegisterCatalogServiceUseCase(repository, ids, clock);
    }

    @Bean
    UpdateCommercialTermsUseCase updateCommercialTerms(CatalogServiceRepository repository, Clock clock,
            SalidaCapacityReader capacity) {
        return new UpdateCommercialTermsUseCase(repository, clock, capacity);
    }

    @Bean
    UpdateDescriptiveInformationUseCase updateDescriptiveInformation(CatalogServiceRepository repository, Clock clock) {
        return new UpdateDescriptiveInformationUseCase(repository, clock);
    }

    @Bean
    InactivateCatalogServiceUseCase inactivateCatalogService(CatalogServiceRepository repository, Clock clock) {
        return new InactivateCatalogServiceUseCase(repository, clock);
    }

    @Bean
    ReactivateCatalogServiceUseCase reactivateCatalogService(CatalogServiceRepository repository, Clock clock) {
        return new ReactivateCatalogServiceUseCase(repository, clock);
    }

    @Bean
    GetCatalogServiceUseCase getCatalogService(CatalogServiceRepository repository) {
        return new GetCatalogServiceUseCase(repository);
    }

    @Bean
    ListCatalogServicesForManagementUseCase listCatalogServicesForManagement(CatalogServiceRepository repository) {
        return new ListCatalogServicesForManagementUseCase(repository);
    }

    @Bean
    ListOfferedCatalogServicesUseCase listOfferedCatalogServices(CatalogServiceRepository repository) {
        return new ListOfferedCatalogServicesUseCase(repository);
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
