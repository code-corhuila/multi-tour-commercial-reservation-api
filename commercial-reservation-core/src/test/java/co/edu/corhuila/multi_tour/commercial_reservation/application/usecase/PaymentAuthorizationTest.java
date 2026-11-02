package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Who may reach the payment routes (api spec 009, RN-10 of the micro): the role gate runs before the body is read.
class PaymentAuthorizationTest {

    private static PaymentActor as(Role role) {
        return new PaymentActor(new UUID(12, 1), new UUID(12, 2), role);
    }

    @Test
    void theAdministratorTheCollaboratorAndTheFinalCustomerRegisterPayments() {
        for (Role role : Set.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.FINAL_CUSTOMER)) {
            assertThatCode(() -> PaymentAuthorization.requireRegister(as(role))).doesNotThrowAnyException();
            assertThatCode(() -> PaymentAuthorization.requireQuote(as(role))).doesNotThrowAnyException();
        }
        for (Role role : Set.of(Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST)) {
            assertThatThrownBy(() -> PaymentAuthorization.requireRegister(as(role))).isInstanceOf(PaymentAccessDeniedException.class);
            assertThatThrownBy(() -> PaymentAuthorization.requireQuote(as(role))).isInstanceOf(PaymentAccessDeniedException.class);
        }
    }

    @Test
    void everyRoleReadsThePaymentsOfAReservationAndAnActorWithoutATenantReadsNothing() {
        for (Role role : Role.values()) assertThatCode(() -> PaymentAuthorization.requireReadPayments(as(role))).doesNotThrowAnyException();
        assertThatThrownBy(() -> PaymentAuthorization.requireReadPayments(new PaymentActor(null, new UUID(12, 2), Role.MANAGER)))
                .isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> PaymentAuthorization.requireRegister(null)).isInstanceOf(PaymentAccessDeniedException.class);
    }

    @Test
    void theParametersAreReadByTheOperatingRolesAndChangedByTheAdministratorOnly() {
        for (Role role : Set.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST)) {
            assertThatCode(() -> PaymentAuthorization.requireReadParameters(as(role))).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> PaymentAuthorization.requireReadParameters(as(Role.FINAL_CUSTOMER))).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatCode(() -> PaymentAuthorization.requireChangeParameters(as(Role.TENANT_ADMINISTRATOR))).doesNotThrowAnyException();
        for (Role role : Set.of(Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER)) {
            assertThatThrownBy(() -> PaymentAuthorization.requireChangeParameters(as(role))).isInstanceOf(PaymentAccessDeniedException.class);
        }
    }
}
