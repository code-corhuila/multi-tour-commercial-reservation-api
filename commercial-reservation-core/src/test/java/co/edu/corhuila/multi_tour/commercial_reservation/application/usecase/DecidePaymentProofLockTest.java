package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// HU-COM-010 RNF-06 (audit of 2026-11-06): the decision of a proof takes the row lock of its reservation inside the transaction, before it
// decides on what it read, so a concurrent decision waits and then finds the final state. The row lock itself is proven over the real
// database in the app module; here the order of the calls is.
class DecidePaymentProofLockTest {

    private static final UUID TENANT = new UUID(11, 1), ADMIN = new UUID(11, 2), CUSTOMER = new UUID(11, 3), SERVICE = new UUID(11, 4),
            DEPARTURE = new UUID(11, 5), RESERVATION = new UUID(11, 6), PROOF = new UUID(11, 7);
    private static final Instant REGISTERED = Instant.parse("2026-10-29T10:00:00Z");
    private static final Instant NOW = REGISTERED.plus(Duration.ofHours(3));

    // Delegates to the fakes and records where the lock was taken.
    private static final class LockSpy implements ReservationRepository, PaymentTransaction {
        final PaymentFakes fakes;
        final List<UUID> locked = new ArrayList<>();
        final List<Boolean> lockedInsideTransaction = new ArrayList<>();
        int depth;

        LockSpy(PaymentFakes fakes) { this.fakes = fakes; }

        @Override public void save(Reservation reservation) { fakes.save(reservation); }
        @Override public Optional<Reservation> findByTenantAndId(UUID tenantId, UUID reservationId) { return fakes.findByTenantAndId(tenantId, reservationId); }
        @Override public Optional<Reservation> findByTenantAndAttempt(UUID tenantId, UUID attemptId) { return fakes.findByTenantAndAttempt(tenantId, attemptId); }

        @Override public Optional<Reservation> lockByTenantAndId(UUID tenantId, UUID reservationId) {
            locked.add(reservationId);
            lockedInsideTransaction.add(depth > 0);
            return fakes.findByTenantAndId(tenantId, reservationId);
        }

        @Override public <T> T inTransaction(Supplier<T> work) {
            depth++;
            try {
                return fakes.inTransaction(work);
            } finally {
                depth--;
            }
        }
    }

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final LockSpy spy = new LockSpy(fakes);
    private final ReviewFakes review = new ReviewFakes(fakes);
    private final DecidePaymentProofUseCase decide = new DecidePaymentProofUseCase(spy, fakes, departures, fakes, fakes, review, review, new CashFakes(), fakes,
            spy, Clock.fixed(NOW, ZoneOffset.UTC));

    LockSpy plant() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plus(Duration.ofDays(5)), DepartureStatus.SCHEDULED, 10, 2));
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, REGISTERED);
        reservation.registerTransfer(PROOF, new Cents(100_000), new Cents(100_000), ADMIN, REGISTERED);
        reservation.allocateSeat(REGISTERED);
        reservation.pullEvents();
        fakes.reservations.put(RESERVATION, reservation);
        return spy;
    }

    private static PaymentActor admin() {
        return new PaymentActor(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR);
    }

    @Test void anApprovalLocksTheReservationOfTheProofInsideTheTransaction() {
        plant();

        decide.recordApproval(admin().tenantId(), admin().actorId(), PROOF, 100_000L, null);

        assertThat(spy.locked).containsExactly(RESERVATION);
        assertThat(spy.lockedInsideTransaction).containsExactly(true);
    }

    @Test void aRejectionLocksTheReservationOfTheProofInsideTheTransaction() {
        plant();

        decide.reject(new RejectProofCommand(admin(), PROOF, "illegible"));

        assertThat(spy.locked).containsExactly(RESERVATION);
        assertThat(spy.lockedInsideTransaction).containsExactly(true);
    }

    @Test void theStepTwoOfTheSagaLocksTheReservationToo() {
        plant();

        decide.recordApproval(TENANT, ADMIN, PROOF, 100_000L, null);

        assertThat(spy.locked).containsExactly(RESERVATION);
        assertThat(spy.lockedInsideTransaction).containsExactly(true);
    }

    @Test void aProofOfAnotherTenantLooksMissingAndNothingIsLocked() {
        plant();
        PaymentActor stranger = new PaymentActor(new UUID(11, 99), ADMIN, Role.TENANT_ADMINISTRATOR);

        assertThatThrownBy(() -> decide.recordApproval(stranger.tenantId(), stranger.actorId(), PROOF, 100_000L, null)).isInstanceOf(PaymentProofNotFoundException.class);
        assertThatThrownBy(() -> decide.reject(new RejectProofCommand(stranger, PROOF, "illegible"))).isInstanceOf(PaymentProofNotFoundException.class);

        assertThat(spy.locked).isEmpty();
    }
}
