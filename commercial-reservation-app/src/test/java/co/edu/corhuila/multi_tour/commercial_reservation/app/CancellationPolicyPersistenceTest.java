package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCancellationPolicies;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-013 DP-02 over the real schema (-db 041): the policy rows are stored per tenant, one for the tenant and one per service; the reader gives
// the service row before the tenant row, nothing when there is none, and never the row of another tenant. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CancellationPolicyPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private JdbcCancellationPolicies policies;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        policies = new JdbcCancellationPolicies(fixture.jdbc);
    }

    private CancellationPolicy row(UUID serviceId, int basisPoints, String reason) {
        return new CancellationPolicy(serviceId, basisPoints, fixture.actor, reason, NOW);
    }

    private int penalty(UUID tenant, UUID service) {
        return policies.penaltyBasisPoints(tenant, service, NOW.plusSeconds(86_400), NOW).orElse(-1);
    }

    @Test
    void aTenantWithNoRowHasNoPenaltyAndTheServiceRowBeatsTheTenantRow() {
        assertThat(policies.penaltyBasisPoints(fixture.tenant, fixture.service, NOW.plusSeconds(86_400), NOW)).isEmpty();

        policies.save(fixture.tenant, row(null, 2_000, "Tenant level"));
        assertThat(penalty(fixture.tenant, fixture.service)).isEqualTo(2_000);

        policies.save(fixture.tenant, row(fixture.service, 5_000, "This tour is stricter"));
        assertThat(penalty(fixture.tenant, fixture.service)).isEqualTo(5_000);
        assertThat(penalty(fixture.tenant, UUID.randomUUID())).isEqualTo(2_000);
    }

    @Test
    void aServiceRowOfZeroMeansNoPenaltyEvenWithAStrictTenantRow() {
        policies.save(fixture.tenant, row(null, 5_000, "Tenant level"));
        policies.save(fixture.tenant, row(fixture.service, 0, "This tour has no penalty"));

        assertThat(penalty(fixture.tenant, fixture.service)).isZero();
    }

    @Test
    void aChangeReplacesTheRowAndKeepsTheLastAuthorReasonAndTime() {
        policies.save(fixture.tenant, row(fixture.service, 5_000, "First"));
        policies.save(fixture.tenant, row(fixture.service, 3_000, "Second"));

        var rows = policies.findByTenant(fixture.tenant);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).penaltyBasisPoints()).isEqualTo(3_000);
        assertThat(rows.get(0).changeReason()).isEqualTo("Second");
        assertThat(rows.get(0).changedBy()).isEqualTo(fixture.actor);
        assertThat(rows.get(0).changedAt()).isEqualTo(NOW);
    }

    @Test
    void anotherTenantNeitherReadsNorSeesTheRowsAndTheServiceCheckIsPerTenant() {
        policies.save(fixture.tenant, row(null, 5_000, "Tenant level"));
        UUID stranger = UUID.randomUUID();

        assertThat(policies.penaltyBasisPoints(stranger, fixture.service, NOW.plusSeconds(86_400), NOW)).isEmpty();
        assertThat(policies.findByTenant(stranger)).isEmpty();
        assertThat(policies.serviceExists(fixture.tenant, fixture.service)).isTrue();
        assertThat(policies.serviceExists(stranger, fixture.service)).isFalse();
    }

    @Test
    void theApplicationUserCannotDeleteARow() {
        policies.save(fixture.tenant, row(null, 5_000, "Tenant level"));

        assertThatThrownBy(() -> fixture.jdbc.update("DELETE FROM commercial_reservation.cancellation_penalty_policy WHERE tenant_id = ?", fixture.tenant))
                .hasStackTraceContaining("permission denied");
    }
}
