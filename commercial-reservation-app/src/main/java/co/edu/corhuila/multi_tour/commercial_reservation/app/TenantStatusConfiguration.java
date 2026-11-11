package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ActiveTenantsController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.TenantEventController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory.CachedTenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcActiveTenantQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerAccountLink;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantDecisions;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusStore;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantSuspensions;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ApplyTenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListActiveTenants;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ActiveTenantQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ListActiveTenantsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.LinkAccountOnEmailVerified;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.MirrorSalidaState;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerAccountLink;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByEmail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantInactivityEffects;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ApplyTenantEventUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.LinkAccountOnEmailVerifiedUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.TenantInactivityUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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
    TenantInactivityEffects tenantInactivityEffects(JdbcTemplate jdbc) {
        // HU-COM-020: the inactivation freezes the deadlines of the tenant and the reactivation resumes them, in the transaction of the copy.
        return new TenantInactivityUseCase(new JdbcTenantSuspensions(jdbc), new JdbcTenantDeadlines(jdbc), new JdbcTenantDecisions(jdbc), new UuidGenerator());
    }

    @Bean
    ApplyTenantEvent applyTenantEvent(TenantStatusStore store, ProcessedEvents processed, TenantEventTransaction transaction, TenantInactivityEffects effects) {
        return new ApplyTenantEventUseCase(store, processed, transaction, effects);
    }

    @Bean
    CustomerAccountLink customerAccountLink(JdbcTemplate jdbc) {
        return new JdbcCustomerAccountLink(jdbc);
    }

    // HU-COM-005 AC-05.7: the same transaction and event ids as the tenant events; it never creates a record.
    @Bean
    LinkAccountOnEmailVerified linkAccountOnEmailVerified(@Lazy CustomerByAccount accounts, @Lazy CustomerByEmail emails, CustomerAccountLink links,
            ProcessedEvents processed, TenantEventTransaction transaction) {
        return new LinkAccountOnEmailVerifiedUseCase(accounts, emails, links, processed, transaction);
    }

    @Bean
    TenantEventController tenantEventController(ApplyTenantEvent apply, LinkAccountOnEmailVerified link,
            @Lazy MirrorSalidaState mirror, ObjectMapper json) {
        return new TenantEventController(apply, link, mirror, json);
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

    // HU-WRK-001 DP-08: the jobs read the active tenants of this copy through an internal route, instead of a provisional variable.
    @Bean
    ActiveTenantQueries activeTenantQueries(JdbcTemplate jdbc) {
        return new JdbcActiveTenantQueries(jdbc);
    }

    @Bean
    ListActiveTenants listActiveTenants(ActiveTenantQueries queries) {
        return new ListActiveTenantsUseCase(queries);
    }

    @Bean
    ActiveTenantsController activeTenantsController(ListActiveTenants tenants) {
        return new ActiveTenantsController(tenants);
    }
}
