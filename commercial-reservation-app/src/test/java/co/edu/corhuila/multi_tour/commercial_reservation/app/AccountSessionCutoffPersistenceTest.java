package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAccountSessionCutoffs;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AccountSessionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ApplyAccountSessionsRevokedUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountSessionsRevoked;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// api spec 022 T-01 (RN-SC-04) over the real schema (db spec 022): every value below is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class AccountSessionCutoffPersistenceTest {
    private static final Instant T0 = Instant.parse("2026-11-12T15:00:00Z");

    private JdbcTemplate jdbc;
    private JdbcAccountSessionCutoffs cutoffs;
    private ApplyAccountSessionsRevokedUseCase apply;
    private final UUID account = UUID.randomUUID();

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        cutoffs = new JdbcAccountSessionCutoffs(jdbc);
        apply = new ApplyAccountSessionsRevokedUseCase(cutoffs, new JdbcProcessedEvents(jdbc),
                new JdbcTenantEventTransaction(new TransactionTemplate(new DataSourceTransactionManager(source))));
    }

    private static boolean present(String name) {
        return System.getenv(name) != null && !System.getenv(name).isBlank();
    }

    private AccountSessionsRevoked event(UUID id, UUID tenant, Instant from) {
        return new AccountSessionsRevoked(id, account, tenant, from);
    }

    private boolean idRecorded(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.processed_event WHERE event_id = ?",
                Integer.class, id) == 1;
    }

    @Test
    void theFirstEventKeepsTheCutoffAndItsIdTogetherAndARepeatChangesNothing() {
        UUID id = UUID.randomUUID();
        assertThat(apply.apply(event(id, null, T0))).isEqualTo(AccountSessionOutcome.APPLIED);
        assertThat(cutoffs.find(account).orElseThrow().revokedFrom()).isEqualTo(T0);
        assertThat(cutoffs.find(account).orElseThrow().tenantId()).isNull();
        assertThat(idRecorded(id)).isTrue();
        assertThat(apply.apply(event(id, null, T0.plusSeconds(60)))).isEqualTo(AccountSessionOutcome.ALREADY_PROCESSED);
        assertThat(cutoffs.find(account).orElseThrow().revokedFrom()).isEqualTo(T0);
    }

    @Test
    void aCutoffMovesForwardAndNeverBack() {
        apply.apply(event(UUID.randomUUID(), null, T0));
        assertThat(apply.apply(event(UUID.randomUUID(), null, T0.minusSeconds(60)))).isEqualTo(AccountSessionOutcome.UNCHANGED);
        assertThat(cutoffs.find(account).orElseThrow().revokedFrom()).isEqualTo(T0);
        assertThat(apply.apply(event(UUID.randomUUID(), null, T0.plusSeconds(60)))).isEqualTo(AccountSessionOutcome.APPLIED);
        assertThat(cutoffs.find(account).orElseThrow().revokedFrom()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void anotherTenantIsRefusedAndLeavesNoIdBehind() {
        UUID tenant = UUID.randomUUID();
        apply.apply(event(UUID.randomUUID(), tenant, T0));
        UUID refused = UUID.randomUUID();
        assertThatThrownBy(() -> apply.apply(event(refused, UUID.randomUUID(), T0.plusSeconds(60)))).isInstanceOf(DomainException.class);
        assertThat(idRecorded(refused)).isFalse();
        assertThat(cutoffs.find(account).orElseThrow().tenantId()).isEqualTo(tenant);
    }
}
