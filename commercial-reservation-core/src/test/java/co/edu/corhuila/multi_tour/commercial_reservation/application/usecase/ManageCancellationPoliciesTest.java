package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangeCancellationPoliciesCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicyValues;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-013 DP-02 (docs pull request 1002), the core part: only the Administrator writes the penalty policy, with a reason and a percentage in
// basis points from 0 to 10 000; the author and the time are the actor's and the clock's; a service of another tenant looks missing.
class ManageCancellationPoliciesTest {

    private static final UUID TENANT = new UUID(13, 1), ADMIN = new UUID(13, 2), TOUR = new UUID(13, 3), STRANGER_TOUR = new UUID(13, 4);
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private final List<CancellationPolicy> rows = new ArrayList<>();
    private final CancellationPolicyRepository repository = new CancellationPolicyRepository() {
        @Override public List<CancellationPolicy> findByTenant(UUID tenantId) {
            return tenantId.equals(TENANT) ? List.copyOf(rows) : List.of();
        }
        @Override public boolean serviceExists(UUID tenantId, UUID serviceId) {
            return tenantId.equals(TENANT) && Set.of(TOUR).contains(serviceId);
        }
        @Override public void save(UUID tenantId, CancellationPolicy policy) {
            rows.removeIf(row -> Objects.equals(row.serviceId(), policy.serviceId()));
            rows.add(policy);
        }
    };
    private final ManageCancellationPoliciesUseCase policies = new ManageCancellationPoliciesUseCase(repository, new Transactions(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static final class Transactions implements co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction {
        @Override public <T> T inTransaction(java.util.function.Supplier<T> work) { return work.get(); }
    }

    private static PaymentActor as(Role role) { return new PaymentActor(TENANT, ADMIN, role); }

    private static ChangeCancellationPoliciesCommand change(Role role, String reason, CancellationPolicyValues... values) {
        return new ChangeCancellationPoliciesCommand(as(role), reason, List.of(values));
    }

    @Test void onlyTheAdministratorChangesThePolicyAndEveryOtherRoleIsRefused() {
        for (Role role : List.of(Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER)) {
            assertThatThrownBy(() -> policies.change(change(role, "Season", new CancellationPolicyValues(TOUR, 5_000))))
                    .isInstanceOf(PaymentAccessDeniedException.class);
        }
        assertThat(rows).isEmpty();
    }

    @Test void theAdministratorStoresThePercentageWithTheAuthorTheReasonAndTheClock() {
        var changed = policies.change(change(Role.TENANT_ADMINISTRATOR, "Seasonal commercial policy",
                new CancellationPolicyValues(TOUR, 5_000), new CancellationPolicyValues(null, 2_000)));

        assertThat(changed).hasSize(2);
        assertThat(rows).extracting(CancellationPolicy::penaltyBasisPoints).containsExactlyInAnyOrder(5_000, 2_000);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.changedBy()).isEqualTo(ADMIN);
            assertThat(row.changeReason()).isEqualTo("Seasonal commercial policy");
            assertThat(row.changedAt()).isEqualTo(NOW);
        });
        assertThat(rows).extracting(CancellationPolicy::serviceId).containsExactlyInAnyOrder(TOUR, null);
    }

    @Test void aChangeKeepsOnlyTheLastRowOfAService() {
        policies.change(change(Role.TENANT_ADMINISTRATOR, "First", new CancellationPolicyValues(TOUR, 5_000)));
        policies.change(change(Role.TENANT_ADMINISTRATOR, "Second", new CancellationPolicyValues(TOUR, 0)));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).penaltyBasisPoints()).isZero();
        assertThat(rows.get(0).changeReason()).isEqualTo("Second");
    }

    @Test void aMissingReasonAPercentageOutOfBoundsOrADuplicatedServiceIsRefusedNamingTheField() {
        assertThatThrownBy(() -> policies.change(change(Role.TENANT_ADMINISTRATOR, "  ", new CancellationPolicyValues(TOUR, 5_000))))
                .isInstanceOf(DomainException.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> policies.change(change(Role.TENANT_ADMINISTRATOR, "Season", new CancellationPolicyValues(TOUR, -1))))
                .isInstanceOf(DomainException.class).hasMessageContaining("penaltyBasisPoints");
        assertThatThrownBy(() -> policies.change(change(Role.TENANT_ADMINISTRATOR, "Season", new CancellationPolicyValues(TOUR, 10_001))))
                .isInstanceOf(DomainException.class).hasMessageContaining("penaltyBasisPoints");
        assertThatThrownBy(() -> policies.change(change(Role.TENANT_ADMINISTRATOR, "Season", new CancellationPolicyValues(TOUR, null))))
                .isInstanceOf(DomainException.class).hasMessageContaining("penaltyBasisPoints");
        assertThatThrownBy(() -> policies.change(change(Role.TENANT_ADMINISTRATOR, "Season")))
                .isInstanceOf(DomainException.class).hasMessageContaining("policies");
        assertThatThrownBy(() -> policies.change(change(Role.TENANT_ADMINISTRATOR, "Season",
                new CancellationPolicyValues(TOUR, 5_000), new CancellationPolicyValues(TOUR, 3_000))))
                .isInstanceOf(DomainException.class).hasMessageContaining("serviceId");
        assertThat(rows).isEmpty();
    }

    @Test void aServiceOfAnotherTenantLooksMissingAndNothingIsWritten() {
        assertThatThrownBy(() -> policies.change(change(Role.TENANT_ADMINISTRATOR, "Season",
                new CancellationPolicyValues(null, 1_000), new CancellationPolicyValues(STRANGER_TOUR, 5_000))))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThat(rows).isEmpty();
    }

    @Test void theStaffRolesReadAndTheFinalCustomerIsRefused() {
        policies.change(change(Role.TENANT_ADMINISTRATOR, "Season", new CancellationPolicyValues(TOUR, 5_000)));

        for (Role role : List.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST)) {
            assertThat(policies.read(as(role))).hasSize(1);
        }
        assertThatThrownBy(() -> policies.read(as(Role.FINAL_CUSTOMER))).isInstanceOf(PaymentAccessDeniedException.class);
        assertThat(policies.read(new PaymentActor(new UUID(13, 99), ADMIN, Role.TENANT_ADMINISTRATOR))).isEmpty();
    }
}
