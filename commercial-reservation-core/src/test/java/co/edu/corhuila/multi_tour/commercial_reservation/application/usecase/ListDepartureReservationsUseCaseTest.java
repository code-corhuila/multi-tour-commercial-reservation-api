package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// HU-COM-012 amendment 2026-11-10, R2 (AC-12.R2.1, AC-12.R2.3, AC-12.R2.4): the reservations of a departure for operations-cost.
class ListDepartureReservationsUseCaseTest {
    private static final String PERMISSION = "MANAGE_DEPARTURE_EXECUTION";

    private final UUID tenant = UUID.randomUUID(), service = UUID.randomUUID(), salida = UUID.randomUUID();
    private final Queries queries = new Queries();
    private final ListDepartureReservationsUseCase useCase = new ListDepartureReservationsUseCase(queries);

    private CustomerActor actor(Role role, String... permissions) {
        return new CustomerActor(tenant, UUID.randomUUID(), role, Set.of(permissions));
    }

    @Test void AC_12_R2_1_theAdministratorReadsTheRowsOfTheDepartureOfItsTenant() {
        var page = useCase.list(actor(Role.TENANT_ADMINISTRATOR), service, salida, 2, 5);
        assertThat(page.items()).extracting(DepartureReservation::customerName).containsExactly("Persona Ficticia");
        assertThat(page.total()).isEqualTo(1);
        assertThat(queries.asked).containsExactly(List.of(tenant, service, salida, 2, 5));
    }

    @Test void AC_12_R2_4_aCollaboratorNeedsThePermissionToExecuteDepartures() {
        assertThat(useCase.list(actor(Role.OPERATIONAL_COLLABORATOR, PERMISSION), service, salida, 1, 20).total()).isEqualTo(1);
        assertThatThrownBy(() -> useCase.list(actor(Role.OPERATIONAL_COLLABORATOR), service, salida, 1, 20))
                .isInstanceOf(ReservationAccessDeniedException.class);
        assertThatThrownBy(() -> useCase.list(actor(Role.OPERATIONAL_COLLABORATOR, "VALIDATE_PAYMENT_PROOFS"), service, salida, 1, 20))
                .isInstanceOf(ReservationAccessDeniedException.class);
        assertThat(queries.asked).hasSize(1);
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER"})
    void AC_12_R2_4_theOtherRolesAreRefusedEvenWithTheClaim(Role role) {
        assertThatThrownBy(() -> useCase.list(actor(role, PERMISSION), service, salida, 1, 20)).isInstanceOf(ReservationAccessDeniedException.class);
        assertThat(queries.asked).isEmpty();
    }

    @Test void AC_12_R2_3_aDepartureTheStoreDoesNotKnowIsNotFound() {
        queries.missing = true;
        assertThatThrownBy(() -> useCase.list(actor(Role.TENANT_ADMINISTRATOR), service, salida, 1, 20)).isInstanceOf(DepartureNotFoundException.class);
    }

    private static final class Queries implements DepartureReservationQueries {
        final List<List<Object>> asked = new ArrayList<>();
        boolean missing;

        @Override public Optional<DepartureReservationPage> ofDeparture(UUID tenantId, UUID serviceId, UUID salidaId, int page, int limit) {
            asked.add(List.of(tenantId, serviceId, salidaId, page, limit));
            if (missing) return Optional.empty();
            return Optional.of(new DepartureReservationPage(List.of(new DepartureReservation(UUID.randomUUID(), "Persona Ficticia", 3,
                    ReservationStatus.CONFIRMED)), 1));
        }
    }
}
