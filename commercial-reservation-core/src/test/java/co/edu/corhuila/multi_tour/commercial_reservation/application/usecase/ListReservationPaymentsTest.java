package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPayments;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-009: the history of a reservation is sorted newest first and paged by the use case, not by the controller.
class ListReservationPaymentsTest {

    private static final UUID TENANT = new UUID(9, 0xe1), ACTOR = new UUID(9, 0xe2), CUSTOMER = new UUID(9, 0xe3), SERVICE = new UUID(9, 0xe4),
            DEPARTURE = new UUID(9, 0xe5), RESERVATION = new UUID(9, 0xe6), FIRST = new UUID(9, 0xe7), SECOND = new UUID(9, 0xe8),
            THIRD = new UUID(9, 0xe9);
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private final Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ACTOR, 2,
            new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
            PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW);
    private final ListReservationPaymentsUseCase list = new ListReservationPaymentsUseCase((tenant, id) -> reservation,
            (tenant, account, id) -> {
                if (!account.equals(CUSTOMER)) throw new IllegalStateException("not the own reservation");
                return reservation;
            });

    ListReservationPaymentsTest() {
        reservation.registerTransfer(FIRST, new Cents(10_000), null, ACTOR, NOW);
        reservation.registerTransfer(SECOND, new Cents(20_000), null, ACTOR, NOW.plusSeconds(60));
        reservation.registerTransfer(THIRD, new Cents(30_000), null, ACTOR, NOW.plusSeconds(120));
    }

    private static PaymentActor staff() {
        return new PaymentActor(TENANT, ACTOR, Role.TENANT_ADMINISTRATOR);
    }

    @Test
    void theNewestAttemptComesFirstAndTheTotalCountsAll() {
        ReservationPayments page = list.list(staff(), RESERVATION, 1, 10);

        assertThat(page.attempts()).extracting("attemptId").containsExactly(THIRD, SECOND, FIRST);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.reservation()).isSameAs(reservation);
    }

    @Test
    void thePageAndTheLimitSliceTheSortedList() {
        assertThat(list.list(staff(), RESERVATION, 2, 2).attempts()).extracting("attemptId").containsExactly(FIRST);
        assertThat(list.list(staff(), RESERVATION, 2, 1).attempts()).extracting("attemptId").containsExactly(SECOND);
        assertThat(list.list(staff(), RESERVATION, 5, 2).attempts()).isEmpty();
        assertThat(list.list(staff(), RESERVATION, 5, 2).total()).isEqualTo(3);
    }

    @Test
    void aFinalCustomerReadsThroughTheOwnScope() {
        PaymentActor customer = new PaymentActor(TENANT, CUSTOMER, Role.FINAL_CUSTOMER);

        assertThat(list.list(customer, RESERVATION, 1, 10).total()).isEqualTo(3);
    }
}
