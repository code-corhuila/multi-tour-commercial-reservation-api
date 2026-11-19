package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.InternalDepartureReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.InternalDepartureReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InternalDepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// G-API6-1 (RN-IDR-01, RN-IDR-03): the reservations of a departure for a service. The store is a fake; the real statements are proved in
// InternalDepartureReservationQueriesPersistenceTest.
class ListInternalDepartureReservationsUseCaseTest {
    private final UUID tenant = UUID.randomUUID(), salida = UUID.randomUUID();
    private final Queries queries = new Queries();
    private final ListInternalDepartureReservationsUseCase useCase = new ListInternalDepartureReservationsUseCase(queries);

    @Test void listsTheReservationsWithTheValidatedAmount() {
        var page = useCase.list(tenant, salida, 2, 5);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).containsExactly(new InternalDepartureReservation(new UUID(9, 1), ReservationStatus.CONFIRMED, 3, new UUID(9, 2), 150_000L));
        assertThat(queries.asked).containsExactly(List.of(tenant, salida, 2, 5));
    }

    @Test void anUnknownDepartureIsNotFound() {
        queries.missing = true;
        assertThatThrownBy(() -> useCase.list(tenant, salida, 1, 20)).isInstanceOf(DepartureNotFoundException.class);
    }

    private static final class Queries implements InternalDepartureReservationQueries {
        final List<List<Object>> asked = new ArrayList<>();
        boolean missing;

        @Override public Optional<InternalDepartureReservationPage> ofDeparture(UUID tenantId, UUID salidaId, int page, int limit) {
            asked.add(List.of(tenantId, salidaId, page, limit));
            if (missing) return Optional.empty();
            return Optional.of(new InternalDepartureReservationPage(List.of(
                    new InternalDepartureReservation(new UUID(9, 1), ReservationStatus.CONFIRMED, 3, new UUID(9, 2), 150_000L)), 1));
        }
    }
}
