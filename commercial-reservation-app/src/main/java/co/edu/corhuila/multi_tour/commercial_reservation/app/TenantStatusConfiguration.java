package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.TenantEventController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory.CachedTenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ApplyTenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ApplyTenantEventUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// HU-WRK-007 (api spec 021): the ports of the tenant status copy. Only this class knows which adapter backs each of them.
@Configuration
class TenantStatusConfiguration {

    @Bean
    TenantStatusStore tenantStatusStore(JdbcTemplate jdbc) {
        return new JdbcTenantStatusStore(jdbc);
    }

    @Bean
    TenantEventTransaction tenantEventTransaction(PlatformTransactionManager manager) {
        return new JdbcTenantEventTransaction(new TransactionTemplate(manager));
    }

    @Bean
    ApplyTenantEvent applyTenantEvent(TenantStatusStore store, ProcessedEvents processed, TenantEventTransaction transaction) {
        return new ApplyTenantEventUseCase(store, processed, transaction);
    }

    @Bean
    TenantEventController tenantEventController(ApplyTenantEvent apply, ObjectMapper json) {
        return new TenantEventController(apply, json);
    }

    @Bean
    TenantDirectory tenantDirectory(JdbcTemplate jdbc) {
        return new JdbcTenantStatusDirectory(jdbc);
    }

    // Kept 5 seconds (DP-TSC-09): the 15-minute token bounds how late an inactivation can be seen (ADR-004 D5).
    @Bean
    TenantStatusReader tenantStatusReader(JdbcTemplate jdbc, Clock clock) {
        return new CachedTenantStatusReader(new JdbcTenantStatusReader(jdbc), clock, Duration.ofSeconds(5));
    }
}
