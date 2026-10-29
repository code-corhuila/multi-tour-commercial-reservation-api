package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AdditionalDiscountFakes.OTHER_REASON;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AdditionalDiscountFakes.REASON;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// T-04: RN-07, AC-04.15 (the registration side of AC-04.8 is in RegisterAdditionalDiscountUseCaseTest).
class AuthorizableReasonsUseCaseTest {
    private final AdditionalDiscountFakes fakes = new AdditionalDiscountFakes()
            .withReason(REASON, "Loyal customer", true);
    private final ManageAuthorizableReasonsUseCase useCase =
            new ManageAuthorizableReasonsUseCase(fakes.reasonStore, () -> new UUID(0, 0xe5));
    private final DiscountRuleActor admin = actor(Role.TENANT_ADMINISTRATOR);

    @Test void theAdministratorListsAddsAndDisables() {
        fakes.withReason(OTHER_REASON, "Second", true);
        var added = useCase.add(admin, "  Group booking ");
        assertThat(added.text()).isEqualTo("Group booking");
        assertThat(added.enabled()).isTrue();
        assertThat(useCase.list(admin)).extracting(AuthorizableReason::text)
                .containsExactlyInAnyOrder("Loyal customer", "Second", "Group booking");
        assertThat(useCase.disable(admin, REASON).enabled()).isFalse();
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   "})
    void anEmptyReasonIsRefused(String text) {
        assertThatThrownBy(() -> useCase.add(admin, text)).isInstanceOf(DomainException.class);
        assertThat(useCase.list(admin)).hasSize(1);
    }

    @Test void aDuplicateReasonIsRefusedIgnoringCaseAndSpaces() {
        assertThatThrownBy(() -> useCase.add(admin, " LOYAL customer ")).isInstanceOf(DomainException.class);
        assertThat(useCase.list(admin)).hasSize(1);
    }

    @Test void theLastEnabledReasonCannotBeDisabled() {
        assertThatThrownBy(() -> useCase.disable(admin, REASON)).isInstanceOf(DomainException.class);
        assertThat(fakes.reasonStore.byId.get(REASON).enabled()).isTrue();
    }

    @Test void aDisabledReasonDoesNotCountAsEnabled() {
        fakes.withReason(OTHER_REASON, "Old reason", false);
        assertThatThrownBy(() -> useCase.disable(admin, REASON)).isInstanceOf(DomainException.class);
    }

    @Test void disablingKeepsTheRecordAndNothingIsDeleted() {
        fakes.withReason(OTHER_REASON, "Second", true);
        useCase.disable(admin, REASON);
        assertThat(fakes.reasonStore.byId).containsKey(REASON);
        assertThat(AuthorizableReasonRepository.class.getMethods()).extracting(Method::getName)
                .noneMatch(name -> name.toLowerCase().contains("delete") || name.toLowerCase().contains("remove"));
    }

    @Test void aMissingReasonIsNotFound() {
        assertThatThrownBy(() -> useCase.disable(admin, new UUID(0, 0xe9)))
                .isInstanceOf(AuthorizableReasonNotFoundException.class);
    }
}
