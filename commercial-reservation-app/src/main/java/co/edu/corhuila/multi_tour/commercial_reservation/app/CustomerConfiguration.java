package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CustomerController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CustomerHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentCustomerRegistration;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.crypto.AesGcmFieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcServiceRiskReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadSensitiveCustomerData;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerProfileReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRegisteredOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipherException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SensitiveDataAccessOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ServiceRiskReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.GetCustomerUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReadSensitiveCustomerDataUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterLeadCustomerUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// HU-COM-005: the customer ports. Only this class knows which adapter backs each of them.
@Configuration
class CustomerConfiguration {

    // Fails closed: the service does not start without a usable key. Neither a key nor a value is ever reported.
    @Bean
    FieldCipher fieldCipher(Environment environment) {
        try {
            return AesGcmFieldCipher.fromConfiguration(environment.getProperty("FIELD_ENCRYPTION_CURRENT_KEY_ID"),
                    environment.getProperty("FIELD_ENCRYPTION_KEYS"));
        } catch (FieldCipherException failure) {
            throw new IllegalStateException("the field cipher cannot start: set FIELD_ENCRYPTION_CURRENT_KEY_ID and "
                    + "FIELD_ENCRYPTION_KEYS (id=base64 of a 32-byte key)", failure);
        }
    }

    @Bean
    JdbcCustomerRepository customerRepository(JdbcTemplate jdbc, FieldCipher cipher) {
        return new JdbcCustomerRepository(jdbc, cipher);
    }

    @Bean
    JdbcCustomerOutbox customerOutbox(JdbcTemplate jdbc) {
        return new JdbcCustomerOutbox(jdbc);
    }

    @Bean
    CustomerTransaction customerTransaction(PlatformTransactionManager manager) {
        return new JdbcCustomerTransaction(new TransactionTemplate(manager));
    }

    @Bean
    ServiceRiskReader serviceRiskReader(JdbcTemplate jdbc) {
        return new JdbcServiceRiskReader(jdbc);
    }

    @Bean
    RegisterLeadCustomer registerLeadCustomer(CustomerRepository repository, CustomerRegisteredOutbox outbox,
            CustomerTransaction transaction, ServiceRiskReader risk, IdGenerator ids, Clock clock) {
        return new RegisterLeadCustomerUseCase(repository, outbox, transaction, risk, ids, clock);
    }

    @Bean
    ReadSensitiveCustomerData readSensitiveCustomerData(CustomerRepository repository,
            SensitiveDataAccessOutbox outbox, CustomerTransaction transaction, Clock clock) {
        return new ReadSensitiveCustomerDataUseCase(repository, outbox, transaction, clock);
    }

    @Bean
    GetCustomer getCustomer(CustomerProfileReader reader) {
        return new GetCustomerUseCase(reader);
    }

    @Bean
    IdempotentCustomerRegistration idempotentCustomerRegistration(RegisterLeadCustomer register, GetCustomer get,
            IdempotencyKeys keys, PlatformTransactionManager manager) {
        return new IdempotentCustomerRegistration(register, get, keys, new TransactionTemplate(manager));
    }

    @Bean
    CustomerController customerController(IdempotentCustomerRegistration registration, GetCustomer get,
            ReadSensitiveCustomerData sensitive) {
        return new CustomerController(registration, get, sensitive);
    }

    @Bean
    WebMvcConfigurer customerAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new CustomerHttpAccess())
                        .addPathPatterns("/api/v1/commercial-reservation/customers/**");
            }
        };
    }
}
