package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExpiryResult;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExpiryVerdict;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-05 and T-06 (application part) of HU-COM-011: RN-06, RN-07, RN-09, RN-10; AC-11.2, AC-11.11, AC-11.14. The job asks, the service
// re-checks at the call and decides; the cancellation, the seat release and the events share one transaction.
class ExpireReservationTest {

    private static final UUID TENANT = new UUID(11, 0xc1), ADMIN = new UUID(11, 0xc2), CUSTOMER = new UUID(11, 0xc3), SERVICE = new UUID(11, 0xc4),
            DEPARTURE = new UUID(11, 0xc5), RESERVATION = new UUID(11, 0xc6), CASH = new UUID(11, 0xc7), PROOF = new UUID(11, 0xc8),
            OTHER_TENANT = new UUID(11, 0xc9);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant DUE = CREATED.plus(Duration.ofHours(24));
    private static final Instant NOW = DUE.plusSeconds(60);

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final ExpiryFakes expiry = new ExpiryFakes(fakes);
    private final ExpireReservationUseCase expire = new ExpireReservationUseCase(fakes, fakes, departures, fakes, expiry, fakes, fakes,
            Clock.fixed(NOW, ZoneOffset.UTC));

    ExpireReservationTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, DUE.plus(Duration.ofDays(30)), DepartureStatus.SCHEDULED, 10, 2));
    }

    private Reservation planted(PaymentType type) {
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                type, PaymentMethod.TRANSFER, CREATED);
        fakes.reservations.put(RESERVATION, reservation);
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE));
        return reservation;
    }

    private static List<String> names(List<PaymentEvent> events) {
        return events.stream().map(e -> e.getClass().getSimpleName()).toList();
    }

    @Test
    void expiryCancelsTheReservationAndReleasesTheSeatInOneTransaction() {
        Reservation reservation = planted(PaymentType.FULL_PAYMENT);
        reservation.allocateSeat(CREATED);
        reservation.pullEvents();

        ExpiryResult result = expire.expire(TENANT, RESERVATION);

        assertThat(result.expired()).isTrue();
        assertThat(result.cause()).isEqualTo(CancellationCause.PAYMENT_EXPIRY);
        assertThat(result.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(fakes.reservations.get(RESERVATION).cancelledAt()).isEqualTo(NOW);
        assertThat(fakes.heldSeats(DEPARTURE)).isZero();
        assertThat(names(fakes.events)).containsExactly("CapacityReleased", "ReservationCancelled");
    }

    @Test
    void aSecondExpiryCallChangesNothing() {
        planted(PaymentType.FULL_PAYMENT);
        expire.expire(TENANT, RESERVATION);
        int saved = fakes.saved.size();
        int events = fakes.events.size();

        ExpiryResult again = expire.expire(TENANT, RESERVATION);

        assertThat(again.expired()).isFalse();
        assertThat(again.reason()).isEqualTo(ExpiryVerdict.ALREADY_CANCELLED);
        assertThat(fakes.saved).hasSize(saved);
        assertThat(fakes.events).hasSize(events);
    }

    @Test
    void theServiceRechecksTheConditionAtTheCall() {
        Reservation paid = planted(PaymentType.FULL_PAYMENT);
        paid.registerCash(CASH, new Cents(300_000), null, ADMIN, CREATED.plus(Duration.ofHours(2)));
        assertThat(expire.expire(TENANT, RESERVATION).reason()).isEqualTo(ExpiryVerdict.PAID);

        Reservation proof = planted(PaymentType.FULL_PAYMENT);
        proof.registerTransfer(PROOF, new Cents(300_000), null, ADMIN, DUE.minusSeconds(5));
        assertThat(expire.expire(TENANT, RESERVATION).reason()).isEqualTo(ExpiryVerdict.PROOF_IN_VALIDATION);

        planted(PaymentType.FULL_PAYMENT);
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE.plus(Duration.ofDays(1))));
        assertThat(expire.expire(TENANT, RESERVATION).reason()).isEqualTo(ExpiryVerdict.NOT_DUE);

        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE, null, null, null, NOW, 30));
        assertThat(expire.expire(TENANT, RESERVATION).reason()).isEqualTo(ExpiryVerdict.FROZEN);

        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
        assertThatThrownBy(() -> expire.expire(OTHER_TENANT, RESERVATION)).isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void aBalanceExpiryCancelsAndHandsTheValuesToPendingTreatment() {
        Reservation reservation = planted(PaymentType.DEPOSIT);
        reservation.registerCash(CASH, new Cents(100_000), new Cents(100_000), ADMIN, CREATED);
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.BALANCE, DUE));

        ExpiryResult result = expire.expire(TENANT, RESERVATION);

        assertThat(result.expired()).isTrue();
        assertThat(expiry.values).containsExactly("PENDING_TREATMENT_AFTER_EXPIRY:100000");
    }
}
