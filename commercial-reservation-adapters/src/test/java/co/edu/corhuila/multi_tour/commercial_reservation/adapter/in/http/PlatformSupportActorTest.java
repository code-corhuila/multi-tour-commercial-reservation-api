package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerAccessDeniedException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleAccessDeniedException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.EstablishmentAccessDeniedException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-SEC-007 G-SEC9-1 (Project Lead, 2026-11-11, option A): the mark of the token, never the role claim, makes the actor PLATFORM_SUPPORT. Without the mark PLATFORM_ADMINISTRATOR still has no actor
// (403), and a forged PLATFORM_SUPPORT in the roles is not read as the role either.
class PlatformSupportActorTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final String SUB = UUID.randomUUID().toString();
    private static final ExceptionalAccess MARK = new ExceptionalAccess("exceptional", Set.of("CATALOG"), UUID.randomUUID());

    private static AuthenticatedPrincipal marked(Set<String> roles) {
        return new AuthenticatedPrincipal(SUB, TENANT, roles, Set.of(), false, MARK);
    }

    private static AuthenticatedPrincipal unmarked(Set<String> roles) {
        return new AuthenticatedPrincipal(SUB, TENANT, roles);
    }

    @Test
    void aMarkedTokenIsThePlatformSupportActorWhateverItsRoles() {
        for (Set<String> roles : new Set[] {Set.of("PLATFORM_ADMINISTRATOR"), Set.of()}) {
            assertThat(CustomerHttpAccess.actor(marked(roles)).role()).isEqualTo(CustomerActor.Role.PLATFORM_SUPPORT);
            assertThat(DiscountRuleHttpAccess.actor(marked(roles)).role()).isEqualTo(DiscountRuleActor.Role.PLATFORM_SUPPORT);
            assertThat(EstablishmentHttpAccess.actor(marked(roles)).role()).isEqualTo(EstablishmentActor.Role.PLATFORM_SUPPORT);
            assertThat(PaymentActors.of(marked(roles)).role()).isEqualTo(PaymentActor.Role.PLATFORM_SUPPORT);
        }
        assertThat(CustomerHttpAccess.actor(marked(Set.of())).actorId()).isEqualTo(UUID.fromString(SUB));
        assertThat(CustomerHttpAccess.actor(marked(Set.of())).tenantId()).isEqualTo(TENANT);
    }

    @Test
    void withoutTheMarkThePlatformAdministratorAndAForgedSupportRoleHaveNoActor() {
        for (String role : new String[] {"PLATFORM_ADMINISTRATOR", "PLATFORM_SUPPORT"}) {
            assertThatThrownBy(() -> CustomerHttpAccess.actor(unmarked(Set.of(role)))).isInstanceOf(CustomerAccessDeniedException.class);
            assertThatThrownBy(() -> DiscountRuleHttpAccess.actor(unmarked(Set.of(role)))).isInstanceOf(DiscountRuleAccessDeniedException.class);
            assertThatThrownBy(() -> EstablishmentHttpAccess.actor(unmarked(Set.of(role)))).isInstanceOf(EstablishmentAccessDeniedException.class);
        }
    }
}
