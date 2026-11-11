package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-008: the reservation answers carry the deadline in force (RN-02: the extension counts, never past the departure start).
// A frozen deadline is still the open one (RN-17); a reservation with no open deadline row has none (G-DL).
class ResolveReservationDeadlineTest {

    private static final UUID TENANT = new UUID(8, 1), CUSTOMER = new UUID(8, 2), SERVICE = new UUID(8, 3), DEPARTURE = new UUID(8, 4),
            RESERVATION = new UUID(8, 5), ADMIN = new UUID(8, 6);
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z"), START = NOW.plus(Duration.ofDays(10));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final ResolveReservationDeadlineUseCase resolver = new ResolveReservationDeadlineUseCase(fakes, departures);

    private Reservation reservation() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, START, DepartureStatus.SCHEDULED, 10, 2));
        return Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofDays(1)));
    }

    private ReservationListItem row() {
        return new ReservationListItem(RESERVATION, "Persona Ficticia", "Tour Ficticio", START, ReservationStatus.PENDING_PAYMENT,
                PaymentStatus.NO_PAYMENT, new Cents(300_000), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, new Cents(0), Optional.empty());
    }

    @Test
    void aReservationWithNoOpenDeadlineHasNone() {
        assertThat(resolver.forReservation(reservation())).isEmpty();
        assertThat(resolver.forRow(TENANT, row())).isEmpty();
    }

    @Test
    void theOpenDeadlineIsReturnedAsStored() {
        Instant due = NOW.plus(Duration.ofHours(24));
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, due));
        assertThat(resolver.forReservation(reservation())).contains(due);
        assertThat(resolver.forRow(TENANT, row())).contains(due);
    }

    @Test
    void theExtensionCountsInTheDeadlineInForce() {
        Instant due = NOW.plus(Duration.ofHours(24));
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, due, 3, NOW, ADMIN, null, null));
        assertThat(resolver.forReservation(reservation())).contains(due.plus(Duration.ofHours(3)));
        assertThat(resolver.forRow(TENANT, row())).contains(due.plus(Duration.ofHours(3)));
    }

    @Test
    void theDeadlineInForceNeverPassesTheDepartureStart() {
        Instant due = START.minus(Duration.ofHours(1));
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, due, 5, NOW, ADMIN, null, null));
        assertThat(resolver.forReservation(reservation())).contains(START);
        assertThat(resolver.forRow(TENANT, row())).contains(START);
    }

    @Test
    void aFrozenDeadlineIsStillTheOpenOne() {
        Instant due = NOW.plus(Duration.ofHours(2));
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, due, null, null, null, NOW, 120));
        assertThat(resolver.forReservation(reservation())).contains(due);
    }
}
