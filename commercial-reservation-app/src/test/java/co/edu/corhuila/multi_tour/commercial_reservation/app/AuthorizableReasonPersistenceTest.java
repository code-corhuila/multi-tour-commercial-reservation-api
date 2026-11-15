package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class AuthorizableReasonPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");
    private JdbcTemplate jdbc;
    private AuthorizableReasonRepository reasons;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null
                && System.getenv("TEST_DATABASE_PASSWORD") != null, "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        reasons = new JdbcAuthorizableReasonRepository(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("commercial_reservation_app");
    }

    @Test
    void anAddedReasonIsFoundByItsTenantAndId() {
        var reason = AuthorizableReason.create(tenant, UUID.randomUUID(), "  Customer loyalty ");
        reasons.save(reason);

        assertThat(reasons.findByTenantAndId(tenant, reason.reasonId())).contains(reason);
        assertThat(jdbc.queryForObject("SELECT status FROM commercial_reservation.authorizable_reason "
                + "WHERE authorizable_reason_id = ?", String.class, reason.reasonId())).isEqualTo("ENABLED");
    }

    @Test
    void anotherTenantNeitherFindsNorListsTheReason() {
        var reason = AuthorizableReason.create(tenant, UUID.randomUUID(), "Group agreement");
        reasons.save(reason);
        UUID other = UUID.randomUUID();

        assertThat(reasons.findByTenantAndId(other, reason.reasonId())).isEmpty();
        assertThat(reasons.findByTenant(other)).isEmpty();
        assertThat(reasons.findByTenant(tenant)).containsExactly(reason);
    }

    @Test
    void disablingKeepsTheRowAndOnlyChangesItsStatus() {
        var reason = AuthorizableReason.create(tenant, UUID.randomUUID(), "Service recovery");
        reasons.save(reason);
        reasons.save(reason.disabled());

        assertThat(reasons.findByTenantAndId(tenant, reason.reasonId())).contains(reason.disabled());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.authorizable_reason "
                + "WHERE tenant_id = ?", Integer.class, tenant)).isEqualTo(1);
    }

    @Test
    void theSameTextTwiceInOneTenantIsRefusedButAnotherTenantMayUseIt() {
        reasons.save(AuthorizableReason.create(tenant, UUID.randomUUID(), "Promotion"));

        assertThatThrownBy(() -> reasons.save(AuthorizableReason.create(tenant, UUID.randomUUID(), " PROMOTION ")))
                .isInstanceOf(DomainException.class).hasMessageContaining("already exists");
        var elsewhere = AuthorizableReason.create(UUID.randomUUID(), UUID.randomUUID(), "Promotion");
        reasons.save(elsewhere);
        assertThat(reasons.findByTenantAndId(elsewhere.tenantId(), elsewhere.reasonId())).contains(elsewhere);
    }

    @Test
    void savingWithTheIdOfAnotherTenantNeverChangesThatRow() {
        var reason = AuthorizableReason.create(tenant, UUID.randomUUID(), "Owner decision");
        reasons.save(reason);
        var hijack = new AuthorizableReason(UUID.randomUUID(), reason.reasonId(), "Owner decision", false);

        assertThatThrownBy(() -> reasons.save(hijack)).isInstanceOf(IllegalStateException.class);
        assertThat(reasons.findByTenantAndId(tenant, reason.reasonId())).contains(reason);
    }
}
