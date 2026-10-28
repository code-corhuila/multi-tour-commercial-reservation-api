package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AdditionalDiscountFakes.REASON;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AdditionalDiscountFakes.RESERVATION;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AdditionalDiscountFakes.reservation;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot.State;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount.Command;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// T-05: RN-08, AC-04.9. All or nothing (plan D-07). TODO DP-08: the read half waits for the owner.
class AdditionalDiscountAuthorizationTest {
    private final AdditionalDiscountFakes fakes = new AdditionalDiscountFakes()
            .withReservation(reservation(State.PENDING_PAYMENT, 1_000_000, 0))
            .withReason(REASON, "Loyal customer", true);
    private final RegisterAdditionalDiscountUseCase register = new RegisterAdditionalDiscountUseCase(fakes, fakes,
            fakes.reasonStore, fakes, fakes, fakes, new DiscountRuleFakes().clock);
    private final ManageAuthorizableReasonsUseCase reasons =
            new ManageAuthorizableReasonsUseCase(fakes.reasonStore, () -> new UUID(0, 0xe5));
    private final Command command = new Command(RESERVATION, REASON, 500);

    @Test void theTenantAdministratorRegistersAndManagesReasons() {
        var admin = actor(Role.TENANT_ADMINISTRATOR);
        assertThat(register.register(admin, command)).isNotNull();
        assertThat(reasons.add(admin, "Group booking")).isNotNull();
        assertThat(reasons.list(admin)).hasSize(2);
        assertThat(reasons.disable(admin, REASON).enabled()).isFalse();
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = "TENANT_ADMINISTRATOR", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherRoleIsRefusedAsAPermissionMatter(Role role) {
        var other = actor(role);
        assertThatThrownBy(() -> register.register(other, command))
                .isInstanceOf(DiscountRuleAccessDeniedException.class).isNotInstanceOf(DomainException.class);
        assertThatThrownBy(() -> register.preview(other, RESERVATION, 500))
                .isInstanceOf(DiscountRuleAccessDeniedException.class);
        assertThatThrownBy(() -> reasons.add(other, "Group booking"))
                .isInstanceOf(DiscountRuleAccessDeniedException.class).isNotInstanceOf(DomainException.class);
        assertThatThrownBy(() -> reasons.disable(other, REASON))
                .isInstanceOf(DiscountRuleAccessDeniedException.class);
        assertThatThrownBy(() -> reasons.list(other)).isInstanceOf(DiscountRuleAccessDeniedException.class);
        assertThat(fakes.discounts).isEmpty();
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.reasonStore.byId).hasSize(1);
        assertThat(fakes.reasonStore.byId.get(REASON).enabled()).isTrue();
    }

    @Test void aMissingActorIsRefused() {
        assertThatThrownBy(() -> register.register(null, command)).isInstanceOf(DiscountRuleAccessDeniedException.class);
        assertThatThrownBy(() -> reasons.list(null)).isInstanceOf(DiscountRuleAccessDeniedException.class);
    }
}
