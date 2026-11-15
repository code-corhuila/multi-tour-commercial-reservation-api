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
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import java.util.Optional;

// HU-COM-011 audit 2026-11-07, decision by default (PDR 1.9 prd.md lines 842 and 843, RN-RES-006, spec RN-06 and RN-10): the expiry looks at
// every open deadline row of the reservation and expires it when ANY row is reached, unpaid, not frozen and without a proof presented in
// time; a row still running never cancels by itself, and the departure start still caps every row.
class ExpireWithSeveralDeadlinesTest {

    private static final UUID TENANT = new UUID(12, 0xc1), ADMIN = new UUID(12, 0xc2), CUSTOMER = new UUID(12, 0xc3), SERVICE = new UUID(12, 0xc4),
            DEPARTURE = new UUID(12, 0xc5), RESERVATION = new UUID(12, 0xc6), CASH = new UUID(12, 0xc7), PROOF = new UUID(12, 0xc8);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant BALANCE_DUE = CREATED.plus(Duration.ofHours(48));
    private static final Instant NOW = BALANCE_DUE.plusSeconds(60);
    private static final Instant LATER = NOW.plus(Duration.ofDays(5));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final ExpiryFakes expiry = new ExpiryFakes(fakes);
    private List<PaymentDeadline> rows = List.of();
    private final PaymentDeadlineReader reader = new PaymentDeadlineReader() {
        @Override public Optional<PaymentDeadline> openDeadline(UUID tenantId, UUID reservationId) {
            return rows.stream().max(java.util.Comparator.comparing(PaymentDeadline::dueAt));
        }

        @Override public List<PaymentDeadline> openDeadlines(UUID tenantId, UUID reservationId) {
            return rows;
        }
    };
    private final ExpireReservationUseCase expire = new ExpireReservationUseCase(fakes, reader, departures, fakes, expiry, fakes, fakes,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private Reservation reservation;

    ExpireWithSeveralDeadlinesTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, LATER.plus(Duration.ofDays(30)), DepartureStatus.SCHEDULED, 10, 2));
        reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, CREATED);
        reservation.registerCash(CASH, new Cents(100_000), new Cents(100_000), ADMIN, CREATED);
        fakes.reservations.put(RESERVATION, reservation);
    }

    private static PaymentDeadline balance(Instant due) { return new PaymentDeadline(DeadlineKind.BALANCE, due); }

    private static PaymentDeadline difference(Instant due) { return new PaymentDeadline(DeadlineKind.PENDING_DIFFERENCE, due); }

    @Test
    void anExpiredUnpaidBalanceCancelsEvenWhenALaterPendingDifferenceIsStillRunning() {
        rows = List.of(balance(BALANCE_DUE), difference(LATER));

        ExpiryResult result = expire.expire(TENANT, RESERVATION);

        assertThat(result.expired()).isTrue();
        assertThat(fakes.reservations.get(RESERVATION).reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void aPendingDifferenceStillRunningNeverCancelsByItselfBeforeItsOwnDueTime() {
        rows = List.of(balance(LATER), difference(LATER.plusSeconds(3_600)));

        ExpiryResult result = expire.expire(TENANT, RESERVATION);

        assertThat(result.expired()).isFalse();
        assertThat(result.reason()).isEqualTo(ExpiryVerdict.NOT_DUE);
        assertThat(fakes.saved).isEmpty();
    }

    @Test
    void aProofInValidationPresentedInTimeStillBlocksTheExpiryOfTheReachedRow() {
        reservation.registerTransfer(PROOF, new Cents(100_000), new Cents(100_000), ADMIN, BALANCE_DUE.minusSeconds(5));
        rows = List.of(balance(BALANCE_DUE), difference(LATER));

        ExpiryResult result = expire.expire(TENANT, RESERVATION);

        assertThat(result.expired()).isFalse();
        assertThat(result.reason()).isEqualTo(ExpiryVerdict.PROOF_IN_VALIDATION);
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void aFrozenReachedRowNeverExpiresAndTheOtherRowStillCountsOnItsOwn() {
        rows = List.of(new PaymentDeadline(DeadlineKind.BALANCE, BALANCE_DUE, null, null, null, NOW, 30), difference(LATER));
        ExpiryResult frozen = expire.expire(TENANT, RESERVATION);
        assertThat(frozen.expired()).isFalse();
        assertThat(frozen.reason()).isEqualTo(ExpiryVerdict.FROZEN);

        rows = List.of(new PaymentDeadline(DeadlineKind.BALANCE, BALANCE_DUE, null, null, null, NOW, 30), difference(BALANCE_DUE.plusSeconds(10)));
        assertThat(expire.expire(TENANT, RESERVATION).expired()).isTrue();
    }

    @Test
    void theDepartureStartStillCapsEveryRow() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.minusSeconds(10), DepartureStatus.SCHEDULED, 10, 2));
        rows = List.of(balance(LATER), difference(LATER.plusSeconds(3_600)));

        assertThat(expire.expire(TENANT, RESERVATION).expired()).isTrue();
    }
}
