package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-06 of HU-COM-020 (-api): RN-09, RN-10; AC-20.10, AC-20.11, AC-20.12. While the tenant is inactive the restricted Administrator decides only the proofs presented before the inactivation; a proof whose
// deadline had expired before it is cancelled at once when the departure time has not arrived, and is never cancelled for non-payment when the departure was cancelled or its time arrived. The cancellation
// of HU-COM-011 is a double; the persistence is a later pull request.
class InactiveTenantProofDecisionTest {

    private static final UUID TENANT = new UUID(20, 0xb1), ADMIN = new UUID(20, 0xb2), CUSTOMER = new UUID(20, 0xb3), SERVICE = new UUID(20, 0xb4), DEPARTURE = new UUID(20, 0xb5),
            RESERVATION = new UUID(20, 0xb6), FIRST = new UUID(20, 0xb7);
    private static final Instant PRESENTED = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant INACTIVATED = PRESENTED.plus(Duration.ofHours(2));
    private static final Instant NOW = PRESENTED.plus(Duration.ofHours(30));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final ReviewFakes review = new ReviewFakes(fakes);
    private final CashFakes cash = new CashFakes();
    private Instant suspendedSince = INACTIVATED;
    private final DecidePaymentProofUseCase decide = new DecidePaymentProofUseCase(fakes, fakes, departures, fakes, fakes, review, review, cash, fakes, fakes,
            Clock.fixed(NOW, ZoneOffset.UTC), tenantId -> Optional.ofNullable(suspendedSince));

    InactiveTenantProofDecisionTest() {
        departureAt(DepartureStatus.SCHEDULED, NOW.plus(Duration.ofDays(5)));
    }

    private void departureAt(DepartureStatus status, Instant startsAt) {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, startsAt, status, 10, 2));
    }

    private static PaymentActor admin() {
        return new PaymentActor(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR);
    }

    // A transfer of 100.000 presented at PRESENTED, and its initial deadline frozen by the inactivation with the minutes it had left (zero: it had expired).
    private void planted(Integer remainingMinutes) {
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(300_000), new Cents(300_000), List.of(), PaymentType.DEPOSIT, PaymentMethod.TRANSFER, PRESENTED);
        reservation.registerTransfer(FIRST, new Cents(100_000), new Cents(100_000), ADMIN, PRESENTED);
        reservation.allocateSeat(PRESENTED);
        reservation.pullEvents();
        fakes.reservations.put(RESERVATION, reservation);
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, PRESENTED.plus(Duration.ofHours(1)), null, null, null, INACTIVATED, remainingMinutes,
                FreezeCause.TENANT_INACTIVE));
    }

    @Test
    void anOverdueProofRejectedBeforeTheScheduledTimeCancelsAtOnce() {
        planted(0);

        decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));

        assertThat(review.cancelled).containsExactly(RESERVATION);
        assertThat(fakes.heldSeats(DEPARTURE)).as("the seat is released").isZero();
    }

    @Test
    void anOverdueProofApprovedForAnInsufficientAmountCancelsAtOnceAndTheValidatedValuesGoToTreatment() {
        planted(0);

        decide.recordApproval(admin().tenantId(), admin().actorId(), FIRST, 60_000L, "only 60.000 arrived");

        assertThat(review.cancelled).containsExactly(RESERVATION);
        assertThat(review.values).containsExactly("PENDING_TREATMENT_AFTER_EXPIRY:60000");
    }

    @Test
    void aDeadlineFrozenByTheInactivationWithTimeLeftNeverCancels() {
        planted(30);

        decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));

        assertThat(review.cancelled).isEmpty();
    }

    @Test
    void aCancelledOrReachedDepartureNeverCancelsForNonPaymentWhateverTheDecision() {
        planted(0);
        departureAt(DepartureStatus.CANCELLED, NOW.plus(Duration.ofDays(5)));
        decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"));
        assertThat(review.cancelled).as("departure cancelled: handled as not attributable, not as non-payment").isEmpty();

        planted(0);
        departureAt(DepartureStatus.SCHEDULED, NOW.minus(Duration.ofMinutes(5)));
        decide.recordApproval(admin().tenantId(), admin().actorId(), FIRST, 60_000L, "only 60.000 arrived");
        assertThat(review.cancelled).as("scheduled time reached").isEmpty();
        assertThat(review.values).isEmpty();
    }

    @Test
    void aProofPresentedAfterTheInactivationIsRefusedToTheRestrictedAdministrator() {
        planted(0);
        suspendedSince = PRESENTED.minus(Duration.ofHours(1));

        assertThatThrownBy(() -> decide.recordApproval(admin().tenantId(), admin().actorId(), FIRST, 100_000L, null)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> decide.reject(new RejectProofCommand(admin(), FIRST, "illegible"))).isInstanceOf(BusinessRuleViolation.class);

        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
        assertThat(fakes.reservations.get(RESERVATION).attempts().get(0).validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
    }
}
