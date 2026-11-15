package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ModificationOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// AC-12.6, AC-12.10 (DP-08): the staff read the history of a reservation, newest first; the customer does not; another tenant looks missing.
class ListReservationModificationsTest {

    private static final UUID TENANT = new UUID(12, 0xe1), USER = new UUID(12, 0xe2), SERVICE = new UUID(12, 0xe3), SALIDA = new UUID(12, 0xe4),
            RESERVATION = new UUID(12, 0xe5);
    private static final Instant T0 = Instant.parse("2026-11-05T10:00:00Z");

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final ReservationModificationFakes history = new ReservationModificationFakes();
    private final ListReservationModificationsUseCase list = new ListReservationModificationsUseCase(fakes, history);

    ListReservationModificationsTest() {
        fakes.reservations.put(RESERVATION, Reservation.create(RESERVATION, TENANT, new UUID(12, 0xe6), SALIDA, USER, 2,
                new ReservedService(SERVICE, new Cents(100), null), new Cents(200), new Cents(200), List.of(), PaymentType.FULL_PAYMENT,
                PaymentMethod.CASH, T0));
        for (int i = 0; i < 3; i++) history.rows.add(row(i));
    }

    private static ReservationModificationRecord row(int index) {
        return new ReservationModificationRecord(new UUID(12, 0xf0 + index), RESERVATION, ChangeType.MODIFICATION, "reason " + index,
                new ModificationOutcome(new Cents(200), new Cents(300), new Cents(200), new Cents(300), new Cents(0), 1), 2, 3, SERVICE, SALIDA,
                SALIDA, USER, T0.plusSeconds(index));
    }

    @Test
    void theStaffReadTheHistoryNewestFirstAndPaged() {
        var page = list.list(new PaymentActor(TENANT, USER, Role.ACCOUNTANT), RESERVATION, 1, 2);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(ReservationModificationRecord::reason).containsExactly("reason 2", "reason 1");
    }

    @Test
    void theCustomerCannotReadAndAnotherTenantLooksMissing() {
        assertThatThrownBy(() -> list.list(new PaymentActor(TENANT, USER, Role.FINAL_CUSTOMER), RESERVATION, 1, 20))
                .isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> list.list(new PaymentActor(new UUID(12, 0xe9), USER, Role.TENANT_ADMINISTRATOR), RESERVATION, 1, 20))
                .isInstanceOf(ReservationNotFoundException.class);
    }
}
